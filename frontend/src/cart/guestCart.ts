// 게스트(비회원) 장바구니는 서버에 저장하지 않고 localStorage 에 보관한다.
// 형식: [{ optionId, quantity }]. 가격/재고/구매가능 여부는 서버 계산 API(/api/cart/guest)로 얻는다.

export interface GuestCartLine {
  optionId: number
  quantity: number
}

const KEY = 'guest_cart'
const EVENT = 'guest-cart-changed'

export function readGuestCart(): GuestCartLine[] {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function write(lines: GuestCartLine[]) {
  localStorage.setItem(KEY, JSON.stringify(lines))
  window.dispatchEvent(new Event(EVENT))
}

/** 같은 옵션은 수량을 합산한다. */
export function addGuestItem(optionId: number, quantity: number) {
  const lines = readGuestCart()
  const existing = lines.find((l) => l.optionId === optionId)
  if (existing) existing.quantity += quantity
  else lines.push({ optionId, quantity })
  write(lines)
}

export function setGuestItemQuantity(optionId: number, quantity: number) {
  if (quantity < 1) return
  const lines = readGuestCart().map((l) => (l.optionId === optionId ? { ...l, quantity } : l))
  write(lines)
}

export function removeGuestItem(optionId: number) {
  write(readGuestCart().filter((l) => l.optionId !== optionId))
}

export function clearGuestCart() {
  localStorage.removeItem(KEY)
  window.dispatchEvent(new Event(EVENT))
}

/** localStorage 변경 구독(같은 탭의 커스텀 이벤트 + 다른 탭의 storage 이벤트). */
export function subscribeGuestCart(listener: () => void): () => void {
  window.addEventListener(EVENT, listener)
  window.addEventListener('storage', listener)
  return () => {
    window.removeEventListener(EVENT, listener)
    window.removeEventListener('storage', listener)
  }
}
