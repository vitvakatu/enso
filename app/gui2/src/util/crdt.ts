import { watchEffect, type Ref } from 'vue'
import type { Awareness } from 'y-protocols/awareness'
import { WebsocketProvider } from 'y-websocket'
import * as Y from 'yjs'
import type { Opt } from './opt'

export function useObserveYjs<T>(
  typeRef: Ref<Opt<Y.AbstractType<T>>>,
  observer: (event: T, transaction: Y.Transaction) => void,
) {
  watchEffect((onCleanup) => {
    const type = typeRef.value
    if (type == null) return

    type.observe(observer)
    onCleanup(() => {
      type.unobserve(observer)
    })
  })
}

export function useObserveYjsDeep(
  typeRef: Ref<Opt<Y.AbstractType<any>>>,
  observer: (event: Y.YEvent<any>[], transaction: Y.Transaction) => void,
) {
  watchEffect((onCleanup) => {
    const type = typeRef.value
    if (type == null) return

    type.observeDeep(observer)
    onCleanup(() => {
      type.unobserveDeep(observer)
    })
  })
}

interface SubdocsEvent {
  loaded: Set<Y.Doc>
  added: Set<Y.Doc>
  removed: Set<Y.Doc>
}

export function attachProvider(url: string, room: string, doc: Y.Doc, awareness: Awareness) {
  const provider = new WebsocketProvider(url, room, doc, { awareness })
  const onSync = () => doc.emit('sync', [true])
  const onDrop = () => doc.emit('sync', [false])

  const attachedSubdocs = new Map<Y.Doc, ReturnType<typeof attachProvider>>()

  function onSubdocs(e: SubdocsEvent) {
    e.loaded.forEach((subdoc) => {
      const subdocRoom = `${room}--${subdoc.guid}`
      attachedSubdocs.set(subdoc, attachProvider(url, subdocRoom, subdoc, awareness))
    })
    e.removed.forEach((subdoc) => {
      const subdocProvider = attachedSubdocs.get(subdoc)
      attachedSubdocs.delete(subdoc)
      if (subdocProvider != null) {
        subdocProvider.dispose()
      }
    })
  }

  provider.on('sync', onSync)
  provider.on('connection-close', onDrop)
  provider.on('connection-error', onDrop)
  doc.on('subdocs', onSubdocs)

  function dispose() {
    provider.disconnect()
    provider.off('sync', onSync)
    provider.off('connection-close', onDrop)
    provider.off('connection-error', onDrop)
    doc.off('subdocs', onSubdocs)
    attachedSubdocs.forEach((subdocProvider) => {
      subdocProvider.dispose()
    })
  }
  return { provider, dispose: dispose }
}
