# Speech input client plan

## Assumptions

- Backend exposes `POST /api/gateway/v1/audio/transcriptions` through the local CLI's Kilo routes.
- Request body is JSON: `{ model, input_audio: { data, format }, language? }`.
- Response body is JSON: `{ text, usage? }`.
- Client MVP is request/response transcription after the user stops recording, not live partial transcription.
- VS Code webviews cannot access `navigator.mediaDevices.getUserMedia`, so microphone capture runs in the extension host.

## Client scope

1. Add an experimental Speech to Text setting, default off.
2. Add an extension-side STT handler that uses the existing Kilo auth stored in the CLI backend.
3. Record microphone audio in the extension host with a bundled FFmpeg helper, falling back to system FFmpeg if needed.
4. Add a shared webview hook for `idle -> recording -> transcribing -> error` state.
5. Add a shared prompt action button that starts/stops recording and shows clean state.
6. Wire the same hook/button into `PromptInput` and Agent Manager `NewWorktreeDialog`.
7. Insert successful transcripts at the current caret, preserve existing prompt content, and never auto-submit.

## Error states

- Experiment disabled: hide the button.
- Missing Kilo login or disabled Kilo provider: disable settings and hide the button.
- Missing bundled helper or OS microphone denial: show a local recording error.
- Empty recording or empty transcript: show a local error.
- Gateway/network failure: show the backend error and keep input unchanged.

## Follow-up

- Replace the static model list with `/api/gateway/v1/transcription-models` discovery if we want automatic model updates.
- Add microphone device selection if users need to choose a non-default input.
