from echo_eval.capture.screenrecord import _device_posix
from echo_eval.device.adb import _remote_arg


def test_double_slash_for_pull():
    assert _remote_arg("/sdcard/echo_eval/a.mp4") == "//sdcard/echo_eval/a.mp4"
    assert _device_posix("/sdcard/echo_eval/a.mp4") == "//sdcard/echo_eval/a.mp4"
