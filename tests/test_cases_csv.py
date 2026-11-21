from pathlib import Path

from echo_eval.cases.excel_cases import load_cases


def test_load_sample_csv():
    path = Path("cases/examples/sample_weather.csv")
    cases = load_cases(path, default_wait_sec=3)
    assert len(cases) == 1
    c = cases[0]
    assert c.case_id == "demo_weather_001"
    assert len(c.steps) == 2
    assert "天气" in c.steps[1].text
    assert c.steps[0].wait_sec == 2.0
    assert c.steps[1].wait_sec == 3.0
    expects = c.exceptions()
    assert any("天气" in e for e in expects)


def test_filter_case_id():
    path = Path("cases/examples/sample_weather.csv")
    cases = load_cases(path, case_id="demo_weather_001")
    assert len(cases) == 1
