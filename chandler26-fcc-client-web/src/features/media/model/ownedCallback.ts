/** One transport callback owner; an old disposer must not clear a replacement subscription. */
export class OwnedCallback<T> {
  private generation = 0;
  private callback?: (event: T) => void;

  subscribe(callback: (event: T) => void): () => void {
    const generation = ++this.generation;
    this.callback = callback;
    return () => { if (generation === this.generation) this.callback = undefined; };
  }

  emit(event: T): void { this.callback?.(event); }
}
