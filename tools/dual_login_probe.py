#!/usr/bin/env python3
"""
WaterWidget 双登录协议探测器。

安全边界：
- 仅调用验证码、登录和只读查询接口。
- 不调用 score-send、dev/start 或 dev/end。
- Token 只保存在进程内存中，不打印、不写入文件。
- 输出只包含响应码、数量、积分估算和不可逆摘要。
"""

from __future__ import annotations

import argparse
import getpass
import hashlib
import http.cookiejar
import json
import os
import random
import re
import string
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


if not sys.stdout.isatty():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if not sys.stderr.isatty():
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")


APP_TYPES = {
    "main": "1,5",
    "app": "1,1",
}
SKIP_KEYWORDS = ("免费权益", "借贷", "贷款")
TOKEN_PATTERN = re.compile(r"\b[a-fA-F0-9]{24,}\b")
PHONE_PATTERN = re.compile(r"^1\d{10}$")
DEFAULT_TIMEOUT_SECONDS = 15
USER_AGENT = "WaterWidget/5.1.0 (dual-login-probe)"


def load_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")) or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def mask_phone(phone: str) -> str:
    if len(phone) == 11:
        return f"{phone[:3]}****{phone[-4:]}"
    return "<masked>"


def safe_message(value: Any, phone: str = "") -> str:
    text = str(value or "").replace("\r", " ").replace("\n", " ").strip()
    if phone:
        text = text.replace(phone, mask_phone(phone))
    text = TOKEN_PATTERN.sub("<redacted>", text)
    return text[:160]


def token_fingerprint(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()[:12]


def captcha_key() -> str:
    alphabet = string.ascii_letters + string.digits
    return "".join(random.SystemRandom().choice(alphabet) for _ in range(10))


def response_code(payload: Any) -> int:
    if not isinstance(payload, dict):
        return -999
    try:
        return int(payload.get("code", -999))
    except (TypeError, ValueError):
        return -999


@dataclass
class JsonResult:
    http_status: int
    payload: dict[str, Any] | None
    error: str = ""

    @property
    def code(self) -> int:
        return response_code(self.payload)


@dataclass
class LoginResult:
    label: str
    app_type: str
    code: int
    token: str = ""
    uid_present: bool = False
    eid_present: bool = False
    error: str = ""

    @property
    def succeeded(self) -> bool:
        return self.code == 0 and bool(self.token)


class ApiClient:
    def __init__(self, gateway: str, cid: str, timeout: int) -> None:
        self.gateway = gateway.rstrip("/")
        self.cid = cid
        self.timeout = timeout
        cookie_jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(cookie_jar)
        )

    @staticmethod
    def headers(app_type: str, token: str = "") -> dict[str, str]:
        headers = {
            "User-Agent": USER_AGENT,
            "Content-Type": "application/json",
            "ApplicationType": app_type,
            "Accept-Language": "zh-Hans-CN;q=1",
        }
        if token:
            headers["Authorization"] = token
        return headers

    def request_bytes(self, path: str) -> bytes:
        request = urllib.request.Request(
            self.gateway + path,
            method="GET",
            headers={"User-Agent": USER_AGENT},
        )
        with self.opener.open(request, timeout=self.timeout) as response:
            return response.read()

    def request_json(
        self,
        method: str,
        path: str,
        app_type: str,
        body: dict[str, Any] | None = None,
        token: str = "",
    ) -> JsonResult:
        encoded_body = None
        if body is not None:
            encoded_body = json.dumps(
                body, ensure_ascii=False, separators=(",", ":")
            ).encode("utf-8")
        request = urllib.request.Request(
            self.gateway + path,
            data=encoded_body,
            method=method,
            headers=self.headers(app_type, token),
        )
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                raw = response.read().decode("utf-8", errors="replace")
                return JsonResult(
                    http_status=response.status,
                    payload=json.loads(raw),
                )
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError:
                payload = None
            return JsonResult(
                http_status=exc.code,
                payload=payload,
                error=f"HTTP {exc.code}",
            )
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            return JsonResult(
                http_status=0,
                payload=None,
                error=type(exc).__name__,
            )

    def download_captcha(self, key: str) -> bytes:
        query = urllib.parse.urlencode(
            {"s": key, "r": int(time.time() * 1000)}
        )
        return self.request_bytes(f"/captcha/?{query}")

    def send_sms(self, phone: str, graph_code: str, key: str) -> JsonResult:
        return self.request_json(
            "POST",
            "/acc/login/code",
            APP_TYPES["main"],
            body={"un": phone, "authCode": graph_code, "s": key},
        )

    def login(
        self, label: str, phone: str, sms_code: str, app_type: str
    ) -> LoginResult:
        result = self.request_json(
            "POST",
            "/acc/login",
            app_type,
            body={
                "openCode": "",
                "authCode": sms_code,
                "un": phone,
                "cid": self.cid,
            },
        )
        payload = result.payload or {}
        al = payload.get("data", {}).get("al", {})
        if not isinstance(al, dict):
            al = {}
        token = str(al.get("token", "") or "")
        message = result.error or safe_message(payload.get("msg", ""), phone)
        return LoginResult(
            label=label,
            app_type=app_type,
            code=result.code,
            token=token,
            uid_present=bool(al.get("uid")),
            eid_present=bool(al.get("eid")),
            error=message,
        )


def write_and_open_captcha(image: bytes, should_open: bool) -> Path:
    temp_dir = Path(tempfile.gettempdir()) / "waterwidget-dual-login-probe"
    temp_dir.mkdir(parents=True, exist_ok=True)
    path = temp_dir / f"captcha-{int(time.time() * 1000)}.png"
    path.write_bytes(image)
    print(f"验证码图片：{path}")
    if should_open and os.name == "nt":
        try:
            os.startfile(path)  # type: ignore[attr-defined]
            print("已调用系统图片查看器打开验证码。")
        except OSError:
            print("自动打开失败，请手动打开上面的图片路径。")
    return path


def summarize_missions(payload: dict[str, Any] | None) -> dict[str, Any]:
    data = payload.get("data", {}) if isinstance(payload, dict) else {}
    missions = data.get("missions", []) if isinstance(data, dict) else []
    if not isinstance(missions, list):
        missions = []

    ref_ids: list[str] = []
    estimated_score = 0
    filtered_score = 0
    for mission in missions:
        if not isinstance(mission, dict):
            continue
        ref_id = str(mission.get("refId", "") or "")
        if ref_id:
            ref_ids.append(ref_id)
        try:
            score = int(mission.get("score", 0) or 0)
            limit = int(mission.get("limit", 0) or 0)
        except (TypeError, ValueError):
            score = 0
            limit = 0
        points = score * limit if score > 0 and limit > 0 else 0
        estimated_score += points
        name = str(mission.get("name", mission.get("title", "")) or "")
        if not any(keyword in name for keyword in SKIP_KEYWORDS):
            filtered_score += points

    digest_source = "\n".join(sorted(ref_ids)).encode("utf-8")
    digest = hashlib.sha256(digest_source).hexdigest()[:12]
    return {
        "count": len(missions),
        "digest": digest,
        "estimated_score": estimated_score,
        "filtered_score": filtered_score,
    }


def probe_token(
    client: ApiClient, token_label: str, token: str
) -> dict[str, dict[str, Any]]:
    matrix: dict[str, dict[str, Any]] = {}
    print(f"\n[{token_label}] Token 指纹 {token_fingerprint(token)}")
    for header_label, app_type in APP_TYPES.items():
        mission_result = client.request_json(
            "GET", "/acc/score/mission-lst", app_type, token=token
        )
        mission_summary = summarize_missions(mission_result.payload)
        view_result = client.request_json(
            "GET", "/acc/view-info", app_type, token=token
        )
        master_result = client.request_json(
            "GET", "/ui/app/master", app_type, token=token
        )
        master_data = (
            master_result.payload.get("data", {})
            if isinstance(master_result.payload, dict)
            else {}
        )
        favos = master_data.get("favos", []) if isinstance(master_data, dict) else []
        device_count = len(favos) if isinstance(favos, list) else 0

        row = {
            "mission_code": mission_result.code,
            "mission_count": mission_summary["count"],
            "mission_digest": mission_summary["digest"],
            "estimated_score": mission_summary["estimated_score"],
            "filtered_score": mission_summary["filtered_score"],
            "view_info_code": view_result.code,
            "master_code": master_result.code,
            "device_count": device_count,
        }
        matrix[header_label] = row
        print(
            "  Header "
            f"{app_type}: mission code={row['mission_code']}, "
            f"任务={row['mission_count']}, "
            f"目录={row['mission_digest']}, "
            f"理论积分={row['estimated_score']}, "
            f"过滤后={row['filtered_score']}; "
            f"view-info={row['view_info_code']}, "
            f"master={row['master_code']}, "
            f"设备数={row['device_count']}"
        )
    return matrix


def print_conclusion(
    logins: list[LoginResult],
    matrices: dict[str, dict[str, dict[str, Any]]],
) -> None:
    successful = [result for result in logins if result.succeeded]
    print("\n===== 自动结论 =====")
    if len(successful) == 2:
        same_token = successful[0].token == successful[1].token
        if same_token:
            print("确认：一条短信验证码可完成两种登录，且服务端返回同一个 Token。")
        else:
            print("确认：一条短信验证码可完成两种登录，并签发两个不同 Token。")
    elif len(successful) == 1:
        print("本轮只有一种登录成功；尚不能确认验证码可复用。")
    else:
        print("两种登录都未成功；请根据登录响应码检查验证码或配置。")

    for label, matrix in matrices.items():
        main = matrix.get("main", {})
        app = matrix.get("app", {})
        if (
            main.get("mission_code") == 0
            and app.get("mission_code") == 0
            and main.get("mission_digest") != app.get("mission_digest")
        ):
            print(f"{label}：同一 Token 切换请求头可得到不同任务目录。")
        elif (
            main.get("mission_code") == 0
            and app.get("mission_code") == 0
            and main.get("mission_digest") == app.get("mission_digest")
        ):
            print(f"{label}：切换请求头后任务目录不变，目录更可能绑定于 Token。")

    print("设备能力仅验证到只读 master 接口；本脚本没有启动或停止任何设备。")


def write_sanitized_report(
    path: Path,
    phone: str,
    logins: list[LoginResult],
    matrices: dict[str, dict[str, dict[str, Any]]],
) -> None:
    successful = [result for result in logins if result.succeeded]
    report = {
        "generated_at": int(time.time()),
        "account": mask_phone(phone),
        "safety": {
            "score_send_called": False,
            "device_start_called": False,
            "device_end_called": False,
            "tokens_persisted": False,
        },
        "logins": [
            {
                "label": result.label,
                "application_type": result.app_type,
                "code": result.code,
                "succeeded": result.succeeded,
                "token_fingerprint": (
                    token_fingerprint(result.token) if result.succeeded else ""
                ),
                "uid_present": result.uid_present,
                "eid_present": result.eid_present,
                "error": safe_message(result.error, phone),
            }
            for result in logins
        ],
        "same_token": (
            len(successful) == 2
            and successful[0].token == successful[1].token
        ),
        "read_only_matrix": matrices,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"脱敏报告已写入：{path}")


def self_test() -> None:
    fake = {
        "code": 0,
        "data": {
            "missions": [
                {"refId": "a", "name": "观看视频", "score": 20, "limit": 5},
                {"refId": "b", "name": "免费权益", "score": 5000, "limit": 1},
            ]
        },
    }
    summary = summarize_missions(fake)
    assert summary["count"] == 2
    assert summary["estimated_score"] == 5100
    assert summary["filtered_score"] == 100
    assert safe_message("token=abcdef0123456789abcdef0123456789") == "token=<redacted>"
    assert mask_phone("13800000000") == "138****0000"
    print("SELF_TEST_OK")


def parse_args() -> argparse.Namespace:
    script_path = Path(__file__).resolve()
    default_config = script_path.parent.parent / "secrets.properties"
    parser = argparse.ArgumentParser(
        description="用一条短信验证码验证双平台登录及只读任务矩阵。"
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=default_config,
        help="secrets.properties 路径（默认使用仓库根目录）",
    )
    parser.add_argument(
        "--phone",
        default="",
        help="可选；不传时在终端交互输入，避免手机号进入命令历史",
    )
    parser.add_argument(
        "--order",
        choices=("main-first", "app-first"),
        default="main-first",
        help="同一短信码的两次登录顺序",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=DEFAULT_TIMEOUT_SECONDS,
        help="单个请求超时秒数",
    )
    parser.add_argument(
        "--no-open",
        action="store_true",
        help="不自动打开验证码图片",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="仅执行离线自测，不发起网络请求",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=None,
        help="可选；将无 Token 的脱敏 JSON 报告写入指定路径",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.self_test:
        self_test()
        return 0

    if not args.config.is_file():
        print(f"错误：找不到配置文件 {args.config}", file=sys.stderr)
        return 2
    properties = load_properties(args.config)
    gateway = properties.get("API_GATEWAY", "").strip()
    cid = properties.get("API_CID", "").strip()
    if not gateway.startswith("https://") or not cid:
        print("错误：API_GATEWAY 或 API_CID 未正确配置。", file=sys.stderr)
        return 2

    phone = args.phone.strip() or input("请输入本人授权测试的手机号：").strip()
    if not PHONE_PATTERN.fullmatch(phone):
        print("错误：手机号格式不正确。", file=sys.stderr)
        return 2

    print("\n===== WaterWidget 双登录只读探测 =====")
    print(f"测试账号：{mask_phone(phone)}")
    print("不会执行积分任务，不会启动或停止饮水设备。")
    print("Token 不打印、不落盘，仅在当前进程内使用。\n")

    client = ApiClient(gateway, cid, max(1, args.timeout))
    key = captcha_key()
    captcha_path: Path | None = None
    try:
        print("[1/5] 获取图形验证码...")
        image = client.download_captcha(key)
        captcha_path = write_and_open_captcha(image, not args.no_open)
        graph_code = input("请查看图片并输入图形验证码：").strip()
        if not graph_code:
            print("错误：图形验证码不能为空。", file=sys.stderr)
            return 2

        print("[2/5] 发送短信验证码...")
        sms_result = client.send_sms(phone, graph_code, key)
        if sms_result.code != 0:
            message = safe_message(
                (sms_result.payload or {}).get("msg", sms_result.error), phone
            )
            print(f"发送失败：code={sms_result.code} {message}")
            return 1
        print("短信已发送。")

        sms_code = getpass.getpass("请输入短信验证码（输入不会回显）：").strip()
        if not sms_code:
            print("错误：短信验证码不能为空。", file=sys.stderr)
            return 2

        labels = (
            ("main", APP_TYPES["main"]),
            ("app", APP_TYPES["app"]),
        )
        if args.order == "app-first":
            labels = tuple(reversed(labels))

        print("[3/5] 使用同一短信码依次请求两种登录...")
        login_results: list[LoginResult] = []
        for label, app_type in labels:
            result = client.login(label, phone, sms_code, app_type)
            login_results.append(result)
            if result.succeeded:
                print(
                    f"  {label} ({app_type})：成功，"
                    f"Token 指纹={token_fingerprint(result.token)}，"
                    f"uid={'有' if result.uid_present else '无'}，"
                    f"eid={'有' if result.eid_present else '无'}"
                )
            else:
                print(
                    f"  {label} ({app_type})：失败，"
                    f"code={result.code} {safe_message(result.error, phone)}"
                )

        successful = [result for result in login_results if result.succeeded]
        if not successful:
            print("没有取得可用 Token，终止只读交叉验证。")
            if args.report is not None:
                write_sanitized_report(
                    args.report, phone, login_results, {}
                )
            return 1

        print("[4/5] 交叉读取任务、账户和设备主页...")
        matrices: dict[str, dict[str, dict[str, Any]]] = {}
        for result in successful:
            matrices[result.label] = probe_token(
                client, result.label, result.token
            )

        print("[5/5] 汇总结论...")
        print_conclusion(login_results, matrices)
        if args.report is not None:
            write_sanitized_report(
                args.report, phone, login_results, matrices
            )
        return 0
    except KeyboardInterrupt:
        print("\n用户取消。")
        return 130
    except (OSError, urllib.error.URLError) as exc:
        print(f"网络或文件错误：{type(exc).__name__}", file=sys.stderr)
        return 1
    finally:
        if captcha_path is not None:
            try:
                captcha_path.unlink(missing_ok=True)
            except OSError:
                pass


if __name__ == "__main__":
    raise SystemExit(main())
