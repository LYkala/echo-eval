"""Load HTTP/HTTPS proxy settings and apply them to process environment."""

from __future__ import annotations

import logging
import os
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import quote

logger = logging.getLogger(__name__)

_PROXY_ENV_KEYS = (
    'http_proxy',
    'https_proxy',
    'HTTP_PROXY',
    'HTTPS_PROXY',
    'no_proxy',
    'NO_PROXY',
)

_DEFAULT_PROBE_URL = 'https://api.moonshot.cn/v1/models'


def _get_config_candidates() -> List[str]:
    """Return proxy config file paths in priority order."""
    testapp_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    webserver_root = os.path.dirname(os.path.abspath(__file__))
    candidates: List[str] = []
    env_path = os.environ.get('PROXY_CONFIG', '').strip()
    if env_path:
        candidates.append(env_path)
    candidates.extend([
        os.path.join(testapp_root, 'configs', 'proxy.yaml'),
        os.path.join(webserver_root, 'configs', 'proxy.yaml'),
    ])
    seen = set()
    ordered: List[str] = []
    for path in candidates:
        if path and path not in seen:
            seen.add(path)
            ordered.append(path)
    return ordered


def _load_yaml_config(path: str) -> Dict[str, Any]:
    try:
        import yaml
    except ImportError as e:
        raise ImportError(
            '缺少 PyYAML 依赖，请在 webserverV2 目录执行: pip install -r requirements.txt'
        ) from e
    with open(path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f) or {}


def _coerce_bool(value: Any, default: bool = False) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return bool(value)
    text = str(value).strip().lower()
    if text in ('1', 'true', 'yes', 'on', 'enabled'):
        return True
    if text in ('0', 'false', 'no', 'off', 'disabled'):
        return False
    return default


def _resolve_mode(config: Dict[str, Any]) -> str:
    """
    Resolve proxy mode: off | on | auto.

    enabled 支持:
      - false / off: 关闭
      - true / on: 强制开启
      - auto / adaptive: 启动时探测网络，自动决定是否走代理
    """
    env_enabled = os.environ.get('PROXY_ENABLED', '').strip().lower()
    if env_enabled in ('auto', 'adaptive'):
        return 'auto'
    if env_enabled:
        return 'on' if _coerce_bool(env_enabled, default=False) else 'off'

    enabled = config.get('enabled', 'auto')
    if isinstance(enabled, str):
        text = enabled.strip().lower()
        if text in ('auto', 'adaptive'):
            return 'auto'
        if text in ('on', 'true', 'yes', '1'):
            return 'on'
        if text in ('off', 'false', 'no', '0'):
            return 'off'
    return 'on' if _coerce_bool(enabled, default=False) else 'off'


def _resolve_proxy_section(config: Dict[str, Any]) -> Dict[str, Any]:
    section = config.get('proxy')
    if isinstance(section, dict):
        return section
    return config


def _resolve_adaptive_section(section: Dict[str, Any]) -> Dict[str, Any]:
    adaptive = section.get('adaptive')
    if isinstance(adaptive, dict):
        return adaptive
    return {}


def _pick(section: Dict[str, Any], *keys: str, default: str = '') -> str:
    for key in keys:
        value = section.get(key)
        if value is not None and str(value).strip() != '':
            return str(value).strip()
    return default


def build_proxy_url(section: Dict[str, Any]) -> str:
    scheme = _pick(section, 'scheme', default='http').rstrip(':/')
    address = _pick(section, 'address', 'host', default='')
    if not address:
        raise ValueError('proxy.address 未配置')
    port_raw = section.get('port', 8080)
    port = int(port_raw)

    user = _pick(section, 'user_id', 'user', default='')
    password = _pick(section, 'user_password', 'password', 'passwd', default='')
    user = os.environ.get('PROXY_USER_ID', user).strip()
    password = os.environ.get('PROXY_USER_PASSWORD', password).strip()

    host_port = f'{address}:{port}'
    if user:
        auth = quote(user, safe='')
        if password:
            auth += ':' + quote(password, safe='')
        return f'{scheme}://{auth}@{host_port}'
    return f'{scheme}://{host_port}'


def _mask_proxy_url(proxy_url: str) -> str:
    if '@' not in proxy_url:
        return proxy_url
    prefix, suffix = proxy_url.split('@', 1)
    scheme_sep = '://'
    if scheme_sep in prefix:
        scheme, _ = prefix.split(scheme_sep, 1)
        return f'{scheme}{scheme_sep}***@{suffix}'
    return f'***@{suffix}'


def _probe_http(url: str, proxy_url: Optional[str], timeout_sec: float) -> Tuple[bool, str]:
    """Return whether outbound HTTP to url succeeds, with a short reason."""
    try:
        import requests
    except ImportError as e:
        raise ImportError(
            '缺少 requests 依赖，请在 webserverV2 目录执行: pip install -r requirements.txt'
        ) from e

    proxies = None
    if proxy_url:
        proxies = {'http': proxy_url, 'https': proxy_url}

    try:
        response = requests.get(
            url,
            timeout=timeout_sec,
            proxies=proxies,
            allow_redirects=True,
        )
        # 能收到 HTTP 响应即说明链路可达（401/403 也算连通）
        return True, f'HTTP {response.status_code}'
    except Exception as exc:
        return False, str(exc)


def _decide_adaptive_proxy(
    section: Dict[str, Any],
    proxy_url: str,
) -> Tuple[bool, str]:
    """
    Probe direct vs proxy connectivity and decide whether to enable proxy.

    Strategy:
      1. Direct probe succeeds -> no proxy
      2. Direct fails, proxy probe succeeds -> enable proxy
      3. Both fail -> keep proxy off and log warning
    """
    adaptive = _resolve_adaptive_section(section)
    probe_url = _pick(
        adaptive,
        'probe_direct_url',
        'probe_url',
        default=_DEFAULT_PROBE_URL,
    )
    env_probe = os.environ.get('PROXY_PROBE_URL', '').strip()
    if env_probe:
        probe_url = env_probe

    try:
        timeout_sec = float(adaptive.get('probe_timeout_sec', 5))
    except (TypeError, ValueError):
        timeout_sec = 5.0
    env_timeout = os.environ.get('PROXY_PROBE_TIMEOUT', '').strip()
    if env_timeout:
        try:
            timeout_sec = float(env_timeout)
        except ValueError:
            pass

    direct_ok, direct_reason = _probe_http(probe_url, proxy_url=None, timeout_sec=timeout_sec)
    if direct_ok:
        return False, f'direct_ok ({direct_reason})'

    proxy_ok, proxy_reason = _probe_http(probe_url, proxy_url=proxy_url, timeout_sec=timeout_sec)
    if proxy_ok:
        return True, f'proxy_required (direct_failed; proxy_ok {proxy_reason})'

    return False, f'both_failed (direct={direct_reason}; proxy={proxy_reason})'


def _apply_proxy_env(proxy_url: str, no_proxy: str) -> None:
    os.environ['http_proxy'] = proxy_url
    os.environ['https_proxy'] = proxy_url
    os.environ['HTTP_PROXY'] = proxy_url
    os.environ['HTTPS_PROXY'] = proxy_url
    if no_proxy:
        os.environ['no_proxy'] = no_proxy
        os.environ['NO_PROXY'] = no_proxy


def apply_proxy_from_config() -> Dict[str, Any]:
    """
    Apply proxy settings from config file to current process environment.

    Returns a dict describing the applied state for logging/diagnostics.
    """
    config_path: Optional[str] = None
    config: Dict[str, Any] = {}
    for path in _get_config_candidates():
        if os.path.isfile(path):
            config_path = path
            config = _load_yaml_config(path)
            break

    mode = _resolve_mode(config)
    result: Dict[str, Any] = {
        'mode': mode,
        'enabled': False,
        'config_path': config_path,
        'proxy_url': None,
        'no_proxy': None,
        'adaptive_reason': None,
    }

    if mode == 'off':
        logger.info('HTTP 代理未启用（enabled=false）')
        return result

    if not config_path:
        paths = ', '.join(_get_config_candidates())
        if mode == 'on':
            logger.warning(
                'PROXY_ENABLED 已开启，但未找到 proxy.yaml，已跳过代理设置。查找路径: %s',
                paths,
            )
        else:
            logger.info('HTTP 代理自适应模式：未找到 proxy.yaml，保持直连')
        return result

    section = _resolve_proxy_section(config)
    try:
        proxy_url = build_proxy_url(section)
    except (TypeError, ValueError) as exc:
        logger.error('proxy.yaml 配置无效 (%s): %s', config_path, exc)
        result['error'] = str(exc)
        return result

    no_proxy = _pick(section, 'no_proxy', default='')
    no_proxy = os.environ.get('NO_PROXY', no_proxy).strip()

    should_enable = mode == 'on'
    if mode == 'auto':
        should_enable, adaptive_reason = _decide_adaptive_proxy(section, proxy_url)
        result['adaptive_reason'] = adaptive_reason
        logger.info('HTTP 代理自适应探测: %s', adaptive_reason)

    if not should_enable:
        logger.info('HTTP 代理未启用（mode=%s）', mode)
        return result

    _apply_proxy_env(proxy_url, no_proxy)
    result['enabled'] = True
    result['proxy_url'] = _mask_proxy_url(proxy_url)
    result['no_proxy'] = no_proxy or None
    logger.info(
        'HTTP 代理已启用: %s (mode=%s, config=%s%s)',
        result['proxy_url'],
        mode,
        config_path,
        f', no_proxy={no_proxy}' if no_proxy else '',
    )
    return result


def clear_proxy_env() -> None:
    """Remove proxy-related environment variables from current process."""
    for key in _PROXY_ENV_KEYS:
        os.environ.pop(key, None)
