import threading
from app.core.config import settings

class WhisperTranscriber:
    """Speech-to-text with faster-whisper. task='translate' turns Hindi/Hinglish speech into
    English text, so the English scam-phrase rules work on it too."""

    def __init__(self, model_size: str):
        self.model_size = model_size
        self._model = None
        self._lock = threading.Lock()

    def _m(self):
        if self._model is None:
            from faster_whisper import WhisperModel
            self._model = WhisperModel(self.model_size, device="cpu", compute_type="int8")
        return self._model

    def segments(self, path: str):
        """[(start_s, end_s, text), ...]"""
        with self._lock:
            segs, _info = self._m().transcribe(path, task="translate", vad_filter=True, beam_size=1)
            return [(s.start, s.end, s.text.strip()) for s in segs]

    def transcribe(self, path: str) -> str:
        return " ".join(t for _, _, t in self.segments(path) if t).strip()

_transcriber = None

def get_transcriber():
    global _transcriber
    if _transcriber is None:
        _transcriber = WhisperTranscriber(settings.whisper_model)
    return _transcriber
