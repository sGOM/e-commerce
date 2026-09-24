import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ImageIcon } from 'lucide-react'
import { toast } from 'sonner'
import { reviewApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { StarRatingDisplay } from './StarRating'
import type { PageResponse, Review, ReviewSort } from '../api/types'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

const sortLabel: Record<ReviewSort, string> = {
  LATEST: '최신순',
  RATING_DESC: '평점 높은순',
}

function ReviewCard({ review, onReport }: { review: Review; onReport: (id: number) => void }) {
  return (
    <li className="rounded-lg border border-border bg-card p-4">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">{review.authorName}</span>
          <StarRatingDisplay rating={review.rating} />
        </div>
        <span className="shrink-0 text-xs text-muted-foreground">
          {new Date(review.createdAt).toLocaleDateString('ko-KR')}
        </span>
      </div>
      <p className="mt-2 whitespace-pre-line text-sm">{review.content}</p>
      {review.imageUrls.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {review.imageUrls.map((url, i) => (
            <a
              key={i}
              href={url}
              target="_blank"
              rel="noreferrer"
              className="block size-20 overflow-hidden rounded-md bg-muted"
            >
              <img
                src={url}
                alt={`${review.authorName}님이 첨부한 리뷰 사진 ${i + 1}`}
                className="size-full object-cover"
              />
            </a>
          ))}
        </div>
      )}
      <div className="mt-2 flex justify-end">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="text-muted-foreground"
          onClick={() => onReport(review.id)}
        >
          신고
        </Button>
      </div>
    </li>
  )
}

/** 상품 상세 리뷰 탭 — 정렬/포토 필터/페이지네이션(AC9), 신고(AC11). */
export default function ProductReviews({ productId }: { productId: number }) {
  const { user } = useAuth()
  const [sort, setSort] = useState<ReviewSort>('LATEST')
  const [photoOnly, setPhotoOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<Review> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    reviewApi
      .byProduct(productId, { sort, photoOnly, page })
      .then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : '리뷰를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [productId, sort, photoOnly, page])

  const report = async (reviewId: number) => {
    if (!user) {
      toast.error('로그인이 필요합니다.')
      return
    }
    const reason = window.prompt('신고 사유를 입력해 주세요.')
    if (!reason) return
    try {
      await reviewApi.report(reviewId, reason)
      toast.success('신고가 접수되었습니다.')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '신고에 실패했습니다.')
    }
  }

  return (
    <section id="reviews" className="scroll-mt-20">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">리뷰 {data ? data.totalElements.toLocaleString('ko-KR') : ''}개</h2>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant={photoOnly ? 'default' : 'outline'}
            size="sm"
            aria-pressed={photoOnly}
            onClick={() => {
              setPhotoOnly((v) => !v)
              setPage(0)
            }}
          >
            <ImageIcon data-icon="inline-start" />
            포토리뷰만
          </Button>
          <Select
            value={sort}
            onValueChange={(v) => {
              setSort(v as ReviewSort)
              setPage(0)
            }}
          >
            <SelectTrigger aria-label="리뷰 정렬" className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(Object.keys(sortLabel) as ReviewSort[]).map((s) => (
                <SelectItem key={s} value={s}>
                  {sortLabel[s]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {loading && (
        <div className="space-y-3">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-24 w-full rounded-lg" />
          ))}
        </div>
      )}

      {error && !loading && <p className="py-6 text-sm text-destructive">{error}</p>}

      {data && !loading && !error && (
        <>
          {data.content.length === 0 ? (
            <p className="py-10 text-center text-sm text-muted-foreground">
              {photoOnly ? '포토리뷰가 없습니다.' : '아직 작성된 리뷰가 없습니다.'}
            </p>
          ) : (
            <ul className="space-y-3">
              {data.content.map((r) => (
                <ReviewCard key={r.id} review={r} onReport={report} />
              ))}
            </ul>
          )}

          {data.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-center gap-3">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                이전
              </Button>
              <span className="text-sm tabular-nums text-muted-foreground">
                {page + 1} / {data.totalPages}
              </span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={page >= data.totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                다음
              </Button>
            </div>
          )}
        </>
      )}

      {!user && (
        <p className="mt-4 text-center text-xs text-muted-foreground">
          구매 후{' '}
          <Link to="/my/reviews" className="underline">
            리뷰를 작성
          </Link>
          하면 포인트를 적립받아요.
        </p>
      )}
    </section>
  )
}
