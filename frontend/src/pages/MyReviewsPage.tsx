import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { toast } from 'sonner'
import { meReviewApi, reviewApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { reviewStatusLabel } from '../labels'
import { StarRatingDisplay, StarRatingInput } from '../components/StarRating'
import type { PageResponse, Review, ReviewableOrderItem } from '../api/types'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import ImageUploadButton from '../components/ImageUploadButton'
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'

const MIN_CONTENT_LENGTH = 10
const MAX_IMAGES = 5

type FormTarget =
  | { mode: 'create'; orderItemId: number; productName: string; optionName: string }
  | { mode: 'edit'; review: Review }

/** 리뷰 작성/수정 공용 폼(Sheet). 사진은 `/api/uploads` 로 올린 URL 을 저장한다. */
function ReviewFormSheet({
  target,
  onClose,
  onSaved,
}: {
  target: FormTarget | null
  onClose: () => void
  onSaved: () => void
}) {
  const [rating, setRating] = useState(0)
  const [content, setContent] = useState('')
  const [images, setImages] = useState<string[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!target) return
    if (target.mode === 'edit') {
      setRating(target.review.rating)
      setContent(target.review.content)
      setImages(target.review.imageUrls)
    } else {
      setRating(0)
      setContent('')
      setImages([])
    }
    setError(null)
  }, [target])

  const title =
    target?.mode === 'create'
      ? `${target.productName} · ${target.optionName}`
      : target?.mode === 'edit'
        ? '리뷰 수정'
        : ''

  const valid = rating >= 1 && content.trim().length >= MIN_CONTENT_LENGTH

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!target || !valid) return
    setSubmitting(true)
    setError(null)
    const imageUrls = images
    try {
      if (target.mode === 'create') {
        await reviewApi.create({ orderItemId: target.orderItemId, rating, content, imageUrls })
        toast.success('리뷰가 등록되었습니다.')
      } else {
        await reviewApi.update(target.review.id, { rating, content, imageUrls })
        toast.success('리뷰가 수정되었습니다.')
      }
      onSaved()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '저장에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Sheet open={target != null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle>{target?.mode === 'create' ? '리뷰 작성' : '리뷰 수정'}</SheetTitle>
        </SheetHeader>
        <form onSubmit={submit} className="flex flex-1 flex-col gap-4 overflow-y-auto px-4">
          {title && <p className="text-sm text-muted-foreground">{title}</p>}

          <div>
            <Label className="mb-2">평점</Label>
            <StarRatingInput value={rating} onChange={setRating} />
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="review-content">내용</Label>
              <span className="text-xs text-muted-foreground">
                {content.length}자 (최소 {MIN_CONTENT_LENGTH}자)
              </span>
            </div>
            <Textarea
              id="review-content"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={5}
              maxLength={2000}
              placeholder="상품에 대한 솔직한 후기를 남겨주세요."
            />
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label>사진 (선택, 최대 {MAX_IMAGES}장)</Label>
              {images.length < MAX_IMAGES && (
                <ImageUploadButton label="사진 추가" onUploaded={(url) => setImages((v) => [...v, url])} />
              )}
            </div>
            {images.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {images.map((url, i) => (
                  <div key={url + i} className="relative">
                    <img src={url} alt={`리뷰 사진 ${i + 1}`} className="size-16 rounded-md object-cover" />
                    <Button
                      type="button"
                      variant="secondary"
                      size="icon-sm"
                      aria-label={`리뷰 사진 ${i + 1} 삭제`}
                      className="absolute -right-2 -top-2 size-6 rounded-full"
                      onClick={() => setImages((v) => v.filter((_, idx) => idx !== i))}
                    >
                      <X className="size-3" />
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <SheetFooter className="mt-auto px-0">
            <Button type="submit" disabled={!valid || submitting} className="w-full">
              {submitting ? '저장 중…' : target?.mode === 'create' ? '리뷰 등록' : '리뷰 수정'}
            </Button>
            <SheetClose asChild>
              <Button type="button" variant="outline" className="w-full">
                취소
              </Button>
            </SheetClose>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}

function ReviewableSection({
  items,
  loading,
  onWrite,
}: {
  items: ReviewableOrderItem[]
  loading: boolean
  onWrite: (item: ReviewableOrderItem) => void
}) {
  if (loading) return <p className="py-6 text-center text-sm text-muted-foreground">불러오는 중…</p>
  if (items.length === 0) {
    return (
      <p className="py-6 text-center text-sm text-muted-foreground">
        작성 가능한 리뷰가 없습니다. 배송완료 후 수령 확인을 하면 이곳에 표시돼요.
      </p>
    )
  }
  return (
    <ul className="space-y-2">
      {items.map((it) => (
        <li
          key={it.orderItemId}
          className="flex items-center justify-between gap-3 rounded-lg border border-border bg-card p-4"
        >
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{it.productName}</p>
            <p className="truncate text-xs text-muted-foreground">{it.optionName}</p>
          </div>
          <Button type="button" size="sm" onClick={() => onWrite(it)} className="shrink-0">
            리뷰 작성
          </Button>
        </li>
      ))}
    </ul>
  )
}

function MyReviewCard({
  review,
  onEdit,
  onDeleted,
}: {
  review: Review
  onEdit: (review: Review) => void
  onDeleted: () => void
}) {
  const [deleting, setDeleting] = useState(false)

  const remove = async () => {
    if (!confirm('리뷰를 삭제하시겠습니까? 삭제해도 이미 적립된 포인트는 회수되지 않습니다.')) return
    setDeleting(true)
    try {
      await reviewApi.remove(review.id)
      toast.success('리뷰가 삭제되었습니다.')
      onDeleted()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '삭제에 실패했습니다.')
    } finally {
      setDeleting(false)
    }
  }

  return (
    <li className="rounded-lg border border-border bg-card p-4">
      <div className="flex items-center justify-between gap-2">
        <StarRatingDisplay rating={review.rating} />
        <div className="flex items-center gap-2">
          {review.status !== 'VISIBLE' && (
            <Badge variant={review.status === 'HIDDEN' ? 'destructive' : 'secondary'}>
              {reviewStatusLabel[review.status]}
            </Badge>
          )}
          <span className="text-xs text-muted-foreground">
            {new Date(review.createdAt).toLocaleDateString('ko-KR')}
          </span>
        </div>
      </div>
      {review.status === 'HIDDEN' && (
        <p className="mt-2 rounded-md bg-muted p-2 text-xs text-muted-foreground">
          관리자 검토에 의해 숨김 처리된 리뷰입니다. 나에게만 보입니다.
        </p>
      )}
      <p className="mt-2 whitespace-pre-line text-sm">{review.content}</p>
      {review.imageUrls.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {review.imageUrls.map((url, i) => (
            <img
              key={i}
              src={url}
              alt={`리뷰 사진 ${i + 1}`}
              className="size-16 rounded-md object-cover"
            />
          ))}
        </div>
      )}
      <div className="mt-3 flex justify-end gap-2">
        <Button type="button" variant="outline" size="sm" onClick={() => onEdit(review)}>
          수정
        </Button>
        <Button type="button" variant="destructive" size="sm" disabled={deleting} onClick={remove}>
          삭제
        </Button>
      </div>
    </li>
  )
}

/** 마이페이지 리뷰 관리: 작성 가능한 리뷰(구매확정 후 미작성) + 내가 쓴 리뷰(HIDDEN 안내 포함). */
export default function MyReviewsPage() {
  const [reviewable, setReviewable] = useState<ReviewableOrderItem[]>([])
  const [reviewableLoading, setReviewableLoading] = useState(true)
  const [myReviews, setMyReviews] = useState<PageResponse<Review> | null>(null)
  const [myReviewsLoading, setMyReviewsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [formTarget, setFormTarget] = useState<FormTarget | null>(null)

  const loadReviewable = () => {
    setReviewableLoading(true)
    meReviewApi
      .reviewable()
      .then(setReviewable)
      .catch((e) => setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.'))
      .finally(() => setReviewableLoading(false))
  }

  const loadMyReviews = () => {
    setMyReviewsLoading(true)
    meReviewApi
      .myReviews()
      .then(setMyReviews)
      .catch((e) => setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.'))
      .finally(() => setMyReviewsLoading(false))
  }

  useEffect(() => {
    loadReviewable()
    loadMyReviews()
  }, [])

  const refreshAll = () => {
    loadReviewable()
    loadMyReviews()
  }

  return (
    <div className="space-y-8">
      <section>
        <h1 className="mb-4 text-xl font-bold">작성 가능한 리뷰</h1>
        {error && <p className="mb-3 text-sm text-destructive">{error}</p>}
        <ReviewableSection
          items={reviewable}
          loading={reviewableLoading}
          onWrite={(it) =>
            setFormTarget({
              mode: 'create',
              orderItemId: it.orderItemId,
              productName: it.productName,
              optionName: it.optionName,
            })
          }
        />
      </section>

      <section>
        <h2 className="mb-4 text-xl font-bold">내가 쓴 리뷰</h2>
        {myReviewsLoading ? (
          <p className="py-6 text-center text-sm text-muted-foreground">불러오는 중…</p>
        ) : !myReviews || myReviews.content.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">작성한 리뷰가 없습니다.</p>
        ) : (
          <ul className="space-y-3">
            {myReviews.content.map((r) => (
              <MyReviewCard
                key={r.id}
                review={r}
                onEdit={(review) => setFormTarget({ mode: 'edit', review })}
                onDeleted={refreshAll}
              />
            ))}
          </ul>
        )}
      </section>

      <ReviewFormSheet target={formTarget} onClose={() => setFormTarget(null)} onSaved={refreshAll} />
    </div>
  )
}
