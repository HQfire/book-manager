# -*- coding: utf-8 -*-
"""One-click dev launcher: MySQL -> Redis -> backend -> frontend.

Starts detached background processes, writes logs under ./logs,
and waits for each port/health endpoint before moving on.
"""
import os
import shutil
import socket
import subprocess
import time
import urllib.request
import webbrowser
from pathlib import Path

ROOT = Path(__file__).resolve().parent
WEB = ROOT / "web"
LOG_DIR = ROOT / "logs"

MVN = r"D:\Apache\apache-maven\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd"
SETTINGS = r"D:\Apache\apache-maven\apache-maven-3.9.16-bin\apache-maven-3.9.16\conf\settings.xml"
REDIS_EXE = r"D:\tools\redis\redis-server.exe"
REDIS_DIR = os.path.dirname(REDIS_EXE)
MYSQL_SERVICE = "MySQL95"
MYSQLD = r"D:\MySQL\bin\mysqld.exe"
MYSQL_DEFAULTS = r"D:\MySQL\my.ini"
SCHEMA_SQL = ROOT / "db" / "schema.sql"
APP_PROPS = ROOT / "src" / "main" / "resources" / "application.properties"
NODE_FALLBACK = r"C:\Users\熊倡\AppData\Local\Doubao\User Data\sandbox_runtime\bases\fa694f517e5995233c80a9a8db5212c2\node"

MYSQL_PORT = 3306
REDIS_PORT = 6379
BACKEND_PORT = 8080
FRONTEND_PORT = 5173

DETACHED = 0x00000008 | 0x00000200 | 0x08000000


def port_open(host, port, timeout=1.0):
    s = socket.socket()
    s.settimeout(timeout)
    try:
        s.connect((host, port))
        return True
    except OSError:
        return False
    finally:
        s.close()


def wait_port(host, port, timeout, step=1.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if port_open(host, port):
            return True
        time.sleep(step)
    return False


def wait_http(url, timeout, step=2.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=3) as resp:
                if resp.status < 500:
                    return True
        except Exception:
            pass
        time.sleep(step)
    return False


def make_env():
    env = os.environ.copy()
    env["PATH"] = r"C:\Windows\System32;" + env.get("PATH", "")
    npm = shutil.which("npm.cmd") or shutil.which("npm")
    if not npm:
        env["PATH"] = NODE_FALLBACK + ";" + env["PATH"]
        npm = os.path.join(NODE_FALLBACK, "npm.cmd")
    return env, npm


def start_detached(argv, cwd, log_path, env):
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log = open(log_path, "w", encoding="utf-8", errors="replace")
    subprocess.Popen(
        argv,
        cwd=str(cwd),
        env=env,
        stdout=log,
        stderr=subprocess.STDOUT,
        creationflags=DETACHED,
    )


def split_sql(sql_text):
    statements, buf = [], []
    for line in sql_text.splitlines():
        line = line.strip()
        if not line or line.startswith("--"):
            continue
        buf.append(line)
        if line.endswith(";"):
            stmt = " ".join(buf).rstrip(";").strip()
            if stmt:
                statements.append(stmt)
            buf = []
    return statements


def read_db_config():
    props = {}
    for line in APP_PROPS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    url = props.get("spring.datasource.url", "")
    db = url.split("/", 3)[-1].split("?")[0] if "/" in url else "book_manager"
    return {
        "db": db,
        "user": props.get("spring.datasource.username", "root"),
        "password": props.get("spring.datasource.password", ""),
    }


def init_database(cfg):
    try:
        import pymysql
    except ImportError:
        print("[warn] pymysql not installed; skip schema auto init")
        return

    conn = None
    for attempt in range(6):
        try:
            conn = pymysql.connect(
                host="127.0.0.1", port=MYSQL_PORT,
                user=cfg["user"], password=cfg["password"],
                connect_timeout=5, charset="utf8mb4",
            )
            break
        except Exception as e:
            if attempt == 5:
                print("[warn] MySQL init connect failed:", e)
                return
            time.sleep(2)

    try:
        with conn.cursor() as cur:
            cur.execute(
                f"CREATE DATABASE IF NOT EXISTS `{cfg['db']}` CHARACTER SET utf8mb4"
            )
            cur.execute(f"USE `{cfg['db']}`")
            cur.execute("SHOW TABLES LIKE 'book'")
            if cur.fetchone() is None:
                print("[..] initializing schema from db/schema.sql ...")
                for stmt in split_sql(SCHEMA_SQL.read_text(encoding="utf-8")):
                    cur.execute(stmt)
                print("[ok] schema initialized")
        conn.commit()
    except Exception as e:
        print("[warn] schema init failed:", e)
    finally:
        conn.close()


def start_mysql(env):
    if port_open("127.0.0.1", MYSQL_PORT):
        print("[ok] MySQL already listening on 3306")
        return True

    print("[..] starting MySQL service ...")
    subprocess.run(
        ["sc.exe", "start", MYSQL_SERVICE],
        capture_output=True,
        text=True,
        env=env,
        check=False,
    )
    if wait_port("127.0.0.1", MYSQL_PORT, 15):
        print("[ok] MySQL up on 3306 (service)")
        return True

    # Service start usually needs admin; fall back to launching mysqld directly.
    print("[..] service start unavailable, launching mysqld directly ...")
    start_detached(
        [MYSQLD, "--defaults-file=" + MYSQL_DEFAULTS],
        os.path.dirname(MYSQLD),
        LOG_DIR / "mysql.log",
        env,
    )
    if wait_port("127.0.0.1", MYSQL_PORT, 30):
        print("[ok] MySQL up on 3306")
        return True

    print("[warn] MySQL did not come up. Start the MySQL95 service in an admin shell,")
    print("       or check logs/mysql.log before relaunching this script.")
    return False


def main():
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    env, npm_cmd = make_env()

    # MySQL: start (if needed), then create database / load schema on an empty DB.
    if not start_mysql(env):
        return
    init_database(read_db_config())

    # Redis
    if port_open("127.0.0.1", REDIS_PORT):
        print("[ok] Redis already listening on 6379")
    else:
        print("[..] starting Redis ...")
        start_detached(
            [REDIS_EXE, "--port", "6379", "--bind", "127.0.0.1"],
            REDIS_DIR,
            LOG_DIR / "redis.log",
            env,
        )
        if wait_port("127.0.0.1", REDIS_PORT, 10):
            print("[ok] Redis up on 6379")
        else:
            print("[warn] Redis did not come up; see logs/redis.log")

    # Backend
    if port_open("127.0.0.1", BACKEND_PORT):
        print("[ok] Backend already listening on 8080")
    else:
        print("[..] starting backend (mvn spring-boot:run) ...")
        start_detached(
            ["cmd", "/c", MVN, "-s", SETTINGS, "spring-boot:run"],
            ROOT,
            LOG_DIR / "backend.log",
            env,
        )
        if wait_http("http://127.0.0.1:8080/actuator/health", 120):
            print("[ok] Backend healthy on http://127.0.0.1:8080")
        else:
            print("[warn] Backend health check timed out; see logs/backend.log")

    # Frontend (Vite may bind ::1 only, so probe over HTTP on localhost)
    frontend_ready = wait_http("http://localhost:5173/", 2)
    if frontend_ready:
        print("[ok] Frontend already listening on 5173")
    else:
        if not (WEB / "node_modules").exists():
            print("[..] installing frontend dependencies (npm install) ...")
            subprocess.run(
                ["cmd", "/c", npm_cmd, "install"],
                cwd=str(WEB),
                env=env,
                check=False,
            )
        print("[..] starting frontend (npm run dev) ...")
        start_detached(
            ["cmd", "/c", npm_cmd, "run", "dev"],
            WEB,
            LOG_DIR / "frontend.log",
            env,
        )
        frontend_ready = wait_http("http://localhost:5173/", 60)
        if frontend_ready:
            print("[ok] Frontend up on http://localhost:5173")
        else:
            print("[warn] Frontend did not respond in time; see logs/frontend.log")

    print("-" * 48)
    print("Frontend: http://localhost:5173")
    print("Backend : http://127.0.0.1:8080")
    print("Health  : http://127.0.0.1:8080/actuator/health")
    print("Logs    :", LOG_DIR)

    if frontend_ready:
        webbrowser.open("http://localhost:5173/")


if __name__ == "__main__":
    main()
