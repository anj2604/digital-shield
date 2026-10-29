import smtplib
from datetime import datetime, timezone
from email.message import EmailMessage
from app.core.config import settings

def send_email(db, to: str, subject: str, body: str) -> str:
    dev = not settings.smtp_host
    # In real mode we never keep the body, because it contains the invite code
    db.outbox.insert_one({"to": to, "subject": subject, "body": body if dev else "[sent by email]",
                          "mode": "dev" if dev else "smtp", "created_at": datetime.now(timezone.utc)})
    if dev:
        print(f"\n===== DEV EMAIL to {to} =====\nSubject: {subject}\n\n{body}\n===== END EMAIL =====\n", flush=True)
        return "dev_outbox"
    msg = EmailMessage()
    msg["From"] = settings.mail_from or settings.smtp_user
    msg["To"] = to
    msg["Subject"] = subject
    msg.set_content(body)
    with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=15) as s:
        s.starttls()
        s.login(settings.smtp_user, settings.smtp_password)
        s.send_message(msg)
    return "sent"
