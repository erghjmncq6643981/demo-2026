# -*- coding: utf-8 -*-
"""复现 vocabulary_scene_unit 场景调用，检查模型返回是否完整。

仅用于本地排障：API Key 从数据库读取后在本进程内解密，不写文件、不打印明文。
"""
import base64
import hashlib
import json
import subprocess
import sys
import urllib.request

sys.path.insert(0, ".")

MYSQL = "/usr/local/mysql/bin/mysql"
DB_ARGS = [MYSQL, "-umicro", "-p123456", "-h127.0.0.1", "study",
           "--default-character-set=utf8mb4", "--raw", "-N"]
API_KEY_SECRET = "dev-learning-assistant-api-key-secret-change-me"  # LearningSecurityProperties 默认值
PREFIX = "enc:v1:"


def query(sql):
    out = subprocess.run(DB_ARGS + ["-e", sql], capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr[:300])
    return [line for line in out.stdout.splitlines() if line.strip()]


def decrypt(stored):
    payload = stored[len(PREFIX):]
    iv_b64, data_b64 = payload.split(".")
    iv = base64.urlsafe_b64decode(iv_b64 + "=" * (-len(iv_b64) % 4))
    data = base64.urlsafe_b64decode(data_b64 + "=" * (-len(data_b64) % 4))
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    key = hashlib.sha256(API_KEY_SECRET.encode()).digest()
    return AESGCM(key).decrypt(iv, data, None).decode()


def main():
    cfg = query("SELECT base_url, model_name, api_key FROM ai_model_config "
                "WHERE provider='kimi' AND model_name='kimi-k2.6' LIMIT 1;")[0].split("\t")
    base_url, model_name, api_key = cfg[0], cfg[1], decrypt(cfg[2])
    system_prompt = query("SELECT system_prompt FROM ai_agent WHERE code='english_vocabulary_plan';")[0]
    template = "\n".join(query("SELECT content FROM ai_prompt_template WHERE id=1201;"))

    rows = query("SELECT original_term, IFNULL(phonetic,''), LEFT(IFNULL(definition_text,''),40) "
                 "FROM vocabulary_catalog_entry WHERE deleted=0 AND published=1 LIMIT 50;")
    candidates = [{"term": r.split("\t")[0], "phonetic": r.split("\t")[1], "definition": r.split("\t")[2]}
                  for r in rows]
    print(f"候选词数量: {len(candidates)}")
    print(f"系统提示词长度: {len(system_prompt)}, 模板长度: {len(template)}")

    prompt = (template
              .replace("{{learning_purpose}}", "综合英语词汇学习")
              .replace("{{unit_no}}", "1")
              .replace("{{candidate_words}}", json.dumps(candidates, ensure_ascii=False))
              .replace("{{review_words}}", "[]")
              .replace("{{target_word_count}}", str(len(candidates))))
    print(f"渲染后用户提示词长度: {len(prompt)}")

    # 与网关实际请求体一致：适配器不发送 temperature / frequency_penalty / presence_penalty
    body = json.dumps({
        "model": model_name,
        "messages": [{"role": "system", "content": system_prompt},
                     {"role": "user", "content": prompt}],
        "max_tokens": 16000,
        "response_format": {"type": "json_object"},
        "thinking": {"type": "disabled"},
    }).encode()

    req = urllib.request.Request(base_url + "/chat/completions", data=body,
                                 headers={"Content-Type": "application/json",
                                          "Authorization": "Bearer " + api_key})
    try:
        with urllib.request.urlopen(req, timeout=400) as resp:
            data = json.loads(resp.read().decode())
    except urllib.error.HTTPError as httpError:
        print(f"HTTP {httpError.code} 错误响应体:")
        print(httpError.read().decode()[:800])
        return

    choice = data.get("choices", [{}])[0]
    content = choice.get("message", {}).get("content") or ""
    usage = data.get("usage", {})
    print(f"\nfinish_reason={choice.get('finish_reason')} "
          f"prompt_tokens={usage.get('prompt_tokens')} completion_tokens={usage.get('completion_tokens')}")
    print(f"content 长度={len(content)}")

    try:
        obj = json.loads(content)
        print("顶层字段:", list(obj.keys()))
        missing = [f for f in ("title", "learning_text", "translation", "vocabulary") if f not in obj]
        print("缺失必需字段:", missing if missing else "无")
        vocab = obj.get("vocabulary")
        if isinstance(vocab, list):
            print(f"vocabulary 词条数: {len(vocab)}")
            if vocab:
                print("词条字段:", list(vocab[0].keys()))
    except Exception as ex:
        print("JSON 解析失败:", ex)
        print("原始内容前 800 字符:\n", content[:800])


if __name__ == "__main__":
    main()
