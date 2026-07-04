import { useEffect, useState } from 'react'

export interface Countdown {
  /** 남은 시간(ms). 이미 지났으면 0. */
  remainingMs: number
  /** 종료 여부(remainingMs <= 0). */
  ended: boolean
  /** "1일 03:20:05" / "03:20:05" 형태로 포맷된 문자열(스크린리더용 aria-label과 별개로 시각 표시용). */
  label: string
}

function format(remainingMs: number): string {
  const totalSec = Math.max(0, Math.floor(remainingMs / 1000))
  const days = Math.floor(totalSec / 86400)
  const hours = Math.floor((totalSec % 86400) / 3600)
  const minutes = Math.floor((totalSec % 3600) / 60)
  const seconds = totalSec % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  const hms = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
  return days > 0 ? `${days}일 ${hms}` : hms
}

/**
 * 종료 시각(endAt, ISO 문자열) 기준 카운트다운. 클라이언트 시계 기준으로 표시만 담당하며(서버가
 * 내려준 endAt을 신뢰), 실제 주문 가능 여부는 서버가 주문 시점에 재검증한다
 * (docs/planning/flash-sale.md §6). 1초 간격 setInterval은 언마운트/endAt 변경 시 정리한다.
 */
export function useCountdown(endAt: string): Countdown {
  const target = new Date(endAt).getTime()
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [endAt])

  const remainingMs = Math.max(0, target - now)
  return { remainingMs, ended: remainingMs <= 0, label: format(remainingMs) }
}
