import { useRef, useState } from 'react'
import { Star } from 'lucide-react'
import { cn } from '@/lib/utils'

interface StarRatingDisplayProps {
  rating: number
  reviewCount?: number
  size?: 'sm' | 'md'
  className?: string
}

/** 평점 표시(읽기 전용). 0.5 단위 반영은 생략하고 실수 비율만큼 별을 채운다(AC8). */
export function StarRatingDisplay({
  rating,
  reviewCount,
  size = 'sm',
  className,
}: StarRatingDisplayProps) {
  const starSize = size === 'md' ? 'size-5' : 'size-3.5'
  const pct = Math.max(0, Math.min(100, (rating / 5) * 100))
  const label =
    reviewCount != null
      ? `평점 5점 만점에 ${rating.toFixed(1)}점, 리뷰 ${reviewCount.toLocaleString('ko-KR')}개`
      : `평점 5점 만점에 ${rating.toFixed(1)}점`

  return (
    <div className={cn('inline-flex items-center gap-1', className)} aria-label={label}>
      <span className="relative inline-flex" aria-hidden="true">
        <span className="flex gap-0.5 text-muted-foreground/30">
          {Array.from({ length: 5 }, (_, i) => (
            <Star key={i} className={cn(starSize, 'fill-current')} />
          ))}
        </span>
        <span
          className="absolute inset-0 flex gap-0.5 overflow-hidden text-amber-400"
          style={{ width: `${pct}%` }}
        >
          {Array.from({ length: 5 }, (_, i) => (
            <Star key={i} className={cn(starSize, 'shrink-0 fill-current')} />
          ))}
        </span>
      </span>
      <span className="text-xs font-medium text-foreground">{rating.toFixed(1)}</span>
      {reviewCount != null && (
        <span className="text-xs text-muted-foreground">
          ({reviewCount.toLocaleString('ko-KR')})
        </span>
      )}
    </div>
  )
}

interface StarRatingInputProps {
  value: number
  onChange: (value: number) => void
  label?: string
}

/**
 * 별점 입력 위젯. `role="radiogroup"` + 개별 별을 `role="radio"` 로 구성해 스크린리더가
 * "1점 중 3점 선택됨" 형태로 안내하도록 한다. 방향키로 이동(roving tabindex), 터치 타깃 44px 이상.
 */
export function StarRatingInput({ value, onChange, label = '별점' }: StarRatingInputProps) {
  const [hover, setHover] = useState<number | null>(null)
  const refs = useRef<(HTMLButtonElement | null)[]>([])
  const display = hover ?? value

  const move = (next: number) => {
    const clamped = Math.min(5, Math.max(1, next))
    onChange(clamped)
    refs.current[clamped - 1]?.focus()
  }

  return (
    <div>
      <div
        role="radiogroup"
        aria-label={label}
        className="flex gap-1"
        onMouseLeave={() => setHover(null)}
      >
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            ref={(el) => {
              refs.current[n - 1] = el
            }}
            type="button"
            role="radio"
            aria-checked={value === n}
            aria-label={`${n}점`}
            tabIndex={value === n || (value === 0 && n === 1) ? 0 : -1}
            className="flex size-11 items-center justify-center rounded-md outline-none focus-visible:ring-2 focus-visible:ring-ring"
            onMouseEnter={() => setHover(n)}
            onFocus={() => setHover(n)}
            onBlur={() => setHover(null)}
            onClick={() => onChange(n)}
            onKeyDown={(e) => {
              if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
                e.preventDefault()
                move(n + 1)
              } else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
                e.preventDefault()
                move(n - 1)
              } else if (e.key === 'Home') {
                e.preventDefault()
                move(1)
              } else if (e.key === 'End') {
                e.preventDefault()
                move(5)
              }
            }}
          >
            <Star
              className={cn(
                'size-7',
                n <= display ? 'fill-amber-400 text-amber-400' : 'fill-none text-muted-foreground',
              )}
            />
          </button>
        ))}
      </div>
      <p role="status" aria-live="polite" className="mt-1 text-xs text-muted-foreground">
        {value > 0 ? `${value}점 선택됨` : '별점을 선택하세요'}
      </p>
    </div>
  )
}
