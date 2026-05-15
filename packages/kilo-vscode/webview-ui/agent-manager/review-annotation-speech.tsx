import { createEffect, createSignal, onCleanup, Show, type Accessor } from "solid-js"
import { render as renderSolid } from "solid-js/web"
import { SpeechToTextButton } from "../src/components/speech-to-text/SpeechToTextButton"
import { insertSpacedText } from "../src/components/chat/prompt-input-utils"
import type { SpeechState, SpeechToText } from "../src/components/speech-to-text/useSpeechToText"
import { reviewAnnotationSpeechKey, type AnnotationMeta } from "./review-annotations"

type Props = {
  speech: SpeechToText
  enabled: Accessor<boolean>
  model: Accessor<string>
  label: (key: string) => string
  keys: Accessor<Set<string>>
}

type Node = {
  host: HTMLDivElement
  dispose: VoidFunction
  setTextarea: (textarea: HTMLTextAreaElement) => void
}

export function insertReviewSpeechText(textarea: HTMLTextAreaElement, value: string): void {
  const text = textarea.value
  const start = textarea.selectionStart ?? text.length
  const end = textarea.selectionEnd ?? start
  const result = insertSpacedText(text, value, start, end)

  textarea.value = result.text
  textarea.setSelectionRange(result.pos, result.pos)
  textarea.focus()
}

export function createReviewAnnotationSpeechRenderer(props: Props) {
  const nodes = new Map<string, Node>()
  const [owner, setOwner] = createSignal<string | undefined>()

  const mount = (key: string, textarea: HTMLTextAreaElement): Node | undefined => {
    if (typeof document === "undefined") return undefined

    const host = document.createElement("div")
    host.className = "am-annotation-speech"
    let field = textarea
    const mine = () => owner() === key
    const state = (): SpeechState => (mine() ? props.speech.state() : "idle")
    const speech: SpeechToText = {
      state,
      error: () => (mine() ? props.speech.error() : undefined),
      active: () => mine() && props.speech.active(),
      start: props.speech.start,
      stop: () => {
        if (!mine()) return
        props.speech.stop()
      },
      cancel: () => {
        if (!mine()) return
        props.speech.cancel()
        setOwner(undefined)
      },
      clear: () => {
        if (!mine()) return
        props.speech.clear()
        setOwner(undefined)
      },
    }
    const blocked = () => props.speech.active() && !mine()
    const start = () => {
      setOwner(key)
      props.speech.start({
        model: props.model(),
        insert: (value) => insertReviewSpeechText(field, value),
      })
    }

    const dispose = renderSolid(
      () => (
        <Show when={props.enabled()}>
          <SpeechToTextButton speech={speech} disabled={blocked()} start={start} label={props.label} />
        </Show>
      ),
      host,
    )

    const node = {
      host,
      dispose,
      setTextarea: (next: HTMLTextAreaElement) => {
        field = next
      },
    }
    nodes.set(key, node)
    return node
  }

  const render = (meta: AnnotationMeta, textarea: HTMLTextAreaElement): HTMLElement | undefined => {
    const key = reviewAnnotationSpeechKey(meta)
    if (!key) return undefined
    const node = nodes.get(key) ?? mount(key, textarea)
    if (!node) return undefined
    node.setTextarea(textarea)
    return node.host
  }

  createEffect(() => {
    const keys = props.keys()
    const current = owner()
    if (current && !keys.has(current)) {
      if (props.speech.active()) props.speech.cancel()
      if (props.speech.state() === "error") props.speech.clear()
      setOwner(undefined)
    }

    for (const [key, node] of nodes) {
      if (keys.has(key)) continue
      node.dispose()
      nodes.delete(key)
    }
  })

  onCleanup(() => {
    if (owner() && props.speech.active()) props.speech.cancel()
    for (const [, node] of nodes) node.dispose()
    nodes.clear()
  })

  return {
    active: props.speech.active,
    render,
  }
}
