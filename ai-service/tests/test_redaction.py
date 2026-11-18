from app.redaction import redact_text, redact_value


def test_redacts_common_secret_shapes() -> None:
    samples = {
        "token ghp_A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8 used": "ghp_A1b2",
        "key AKIAABCDEFGHIJKLMNOP denied": "AKIAABCDEFGHIJKLMNOP",
        "password=Sup3rS3cret! for user": "Sup3rS3cret!",
        "api_key: 'abc123xyz'": "abc123xyz",
        "postgres://svc:hunter2@db.internal:5432/x": "hunter2",
        "Authorization: Bearer abcdefghijklmnop.qrs": "abcdefghijklmnop",
        "jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U": "eyJhbGci",
        "contact ops@example.com now": "ops@example.com",
        "from 10.2.3.4 port 22": "10.2.3.4",
        "sk-abcdefghijklmnopqrstuvwxyz123": "sk-abcdefghij",
    }
    for text, secret in samples.items():
        result = redact_text(text)
        assert secret not in result.value, text
        assert result.count >= 1


def test_redaction_keeps_diagnostic_text() -> None:
    result = redact_text("java.lang.OutOfMemoryError: Java heap space at 14:02")
    assert result.value == "java.lang.OutOfMemoryError: Java heap space at 14:02"
    assert result.count == 0


def test_redact_value_is_recursive_and_counts() -> None:
    result = redact_value({"logs": [{"message": "password=x1 and token=y2"}], "n": 3})
    assert "x1" not in str(result.value) and "y2" not in str(result.value)
    assert result.value["n"] == 3
    assert result.count == 2
    assert result.kinds == {"SECRET_ASSIGNMENT": 2}
