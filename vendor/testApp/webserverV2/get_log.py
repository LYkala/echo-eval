import os
import logging

from proxy_config import apply_proxy_from_config

apply_proxy_from_config()

import requests

LOG_DIR = os.path.join(os.path.dirname(__file__), 'logs')

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(os.path.join(LOG_DIR, 'webserver.log')),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

def download_log(account, server_url="http://127.0.0.1:9527/upload_and_get_log", file_dir=None, timeout_sec=60):
    """
    向日志服务发起请求并保存返回的日志文件到测试结果文件夹.

    Args:
        account: 账户ID
        server_url: 日志服务URL
        file_dir: 用于保存到对应的测试结果文件夹
    """
    payload = {
        "account": account,
    }

    try:
        logger.info(f"正在请求日志: Account={account}")
        # 使用 stream=True 可以处理大文件，避免内存溢出
        response = requests.post(server_url, json=payload, stream=True, timeout=timeout_sec)

        if response.status_code == 200:
            # 从 Content-Disposition 响应头中提取文件名
            # 格式通常为: attachment; filename="aisf_user1_1712345678.log"
            content_disposition = response.headers.get('Content-Disposition')
            if content_disposition and 'filename=' in content_disposition:
                filename = content_disposition.split('filename=')[-1].strip('"')
            else:
                filename = f"log_{account}.log"

            #
            if file_dir:
                # 获取webserver_v2.py的路径
                os.makedirs(file_dir, exist_ok=True)
                save_path = os.path.join(file_dir, filename)
            else:
                # 默认保存到当前目录
                save_path = os.path.join(os.getcwd(), filename)

            with open(save_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)

            logger.info(f"✅ 日志下载成功！保存位置: {save_path}")
            return save_path

        else:
            # 如果失败，服务端会返回 JSON 格式的错误信息
            try:
                error_msg = response.json()
            except:
                error_msg = response.text
            logger.error(f"❌ 下载失败 (HTTP {response.status_code}): {error_msg}")
            return None

    except requests.exceptions.ConnectionError as e:
        logger.error(
            f"❌ 无法连接到日志服务 {server_url}，"
            f"请确认该地址可从 Web 服务器访问且服务已启动。详情: {e}"
        )
        return None
    except requests.exceptions.Timeout:
        logger.error(f"❌ 请求日志服务超时 ({timeout_sec}s): {server_url}")
        return None
    except Exception as e:
        logger.error(f"❌ 下载日志发生错误 ({server_url}): {e}")
        return None


if __name__ == "__main__":
    # 配置参数
    SERVICE_ENDPOINT = "http://115.120.249.45:40096/upload_and_get_log"
    TEST_ACCOUNT = "2047158695303999489"
    TEST_IP = "9.15.125.160"  # 这里的 IP 是你要让服务端去 curl 的那个设备 IP

    download_log(TEST_ACCOUNT, SERVICE_ENDPOINT)
