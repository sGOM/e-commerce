import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { adminCollectionApi, productApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { collectionStatusLabel } from '../../labels'
import type { CollectionDetail, CollectionProductItem, CollectionStatus, ProductSummary } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'

/** ISO(Instant) 문자열 -> <input type="datetime-local"> value (로컬 타임존 기준). */
function toDatetimeLocal(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const nextStatuses: Record<CollectionStatus, CollectionStatus[]> = {
  DRAFT: ['PUBLISHED'],
  PUBLISHED: ['ENDED'],
  ENDED: [],
}

/** 메타(제목/배너/기간) 등록·수정 폼. 신규 등록은 저장 후 편집 화면(상품 편성 가능)으로 이동한다. */
function MetaForm({
  collection,
  onSaved,
}: {
  collection: CollectionDetail | null
  onSaved: (c: CollectionDetail) => void
}) {
  const navigate = useNavigate()
  const [title, setTitle] = useState(collection?.title ?? '')
  const [subtitle, setSubtitle] = useState(collection?.subtitle ?? '')
  const [bannerImageUrl, setBannerImageUrl] = useState(collection?.bannerImageUrl ?? '')
  const [startAt, setStartAt] = useState(collection ? toDatetimeLocal(collection.startAt) : '')
  const [endAt, setEndAt] = useState(collection ? toDatetimeLocal(collection.endAt) : '')
  const [displayOrder, setDisplayOrder] = useState(collection?.displayOrder ?? 0)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const body = {
        title,
        subtitle: subtitle || undefined,
        bannerImageUrl: bannerImageUrl || undefined,
        startAt: new Date(startAt).toISOString(),
        endAt: new Date(endAt).toISOString(),
        displayOrder,
      }
      const saved = collection
        ? await adminCollectionApi.update(collection.id, body)
        : await adminCollectionApi.create(body)
      toast.success(collection ? '기획전 정보를 수정했습니다.' : '기획전을 등록했습니다.')
      onSaved(saved)
      if (!collection) navigate(`/admin/collections/${saved.id}`, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '저장 실패')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card>
      <CardContent>
        <form onSubmit={submit} className="space-y-3">
          <h2 className="font-bold">기획전 정보</h2>
          <Input
            required
            placeholder="제목"
            aria-label="제목"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <Input
            placeholder="부제(선택)"
            aria-label="부제"
            value={subtitle}
            onChange={(e) => setSubtitle(e.target.value)}
          />
          <Input
            placeholder="배너 이미지 URL(선택)"
            aria-label="배너 이미지 URL"
            value={bannerImageUrl}
            onChange={(e) => setBannerImageUrl(e.target.value)}
          />
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="collection-start" className="text-xs text-muted-foreground">
                노출 시작
              </Label>
              <Input
                id="collection-start"
                required
                type="datetime-local"
                value={startAt}
                onChange={(e) => setStartAt(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="collection-end" className="text-xs text-muted-foreground">
                노출 종료
              </Label>
              <Input
                id="collection-end"
                required
                type="datetime-local"
                value={endAt}
                onChange={(e) => setEndAt(e.target.value)}
              />
            </div>
          </div>
          <div className="space-y-1">
            <Label htmlFor="collection-order" className="text-xs text-muted-foreground">
              노출 순서(작을수록 먼저 노출)
            </Label>
            <Input
              id="collection-order"
              type="number"
              min={0}
              value={displayOrder}
              onChange={(e) => setDisplayOrder(Number(e.target.value))}
              className="w-32"
            />
          </div>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          <Button type="submit" disabled={saving}>
            {saving ? '저장 중…' : collection ? '수정 저장' : '등록'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

/** 상태 전이 버튼(DRAFT -> PUBLISHED -> ENDED). */
function StatusPanel({
  collection,
  onChanged,
}: {
  collection: CollectionDetail
  onChanged: (c: CollectionDetail) => void
}) {
  const [error, setError] = useState<string | null>(null)
  const [changing, setChanging] = useState(false)
  const nexts = nextStatuses[collection.status]

  const change = async (next: CollectionStatus) => {
    setChanging(true)
    setError(null)
    try {
      const updated = await adminCollectionApi.changeStatus(collection.id, next)
      onChanged(updated)
      toast.success(`상태를 ${collectionStatusLabel[next]}(으)로 변경했습니다.`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '상태 변경 실패')
    } finally {
      setChanging(false)
    }
  }

  return (
    <Card className="flex-row items-center gap-3 p-4">
      <span className="text-sm text-muted-foreground">현재 상태</span>
      <span className="rounded bg-muted px-2 py-0.5 text-sm font-semibold text-foreground">
        {collectionStatusLabel[collection.status]}
      </span>
      {nexts.length === 0 ? (
        <span className="text-xs text-muted-foreground">더 이상 전이할 상태가 없습니다.</span>
      ) : (
        <div className="ml-auto flex gap-2">
          {nexts.map((n) => (
            <Button key={n} type="button" variant="outline" size="sm" disabled={changing} onClick={() => change(n)}>
              {collectionStatusLabel[n]}(으)로 전환
            </Button>
          ))}
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </Card>
  )
}

/** 상품 편성 — 검색해서 추가, 순서는 위/아래 버튼으로 조정, 저장 시 productIds 전체를 교체 전송한다. */
function ProductAssignmentPanel({
  collection,
  onChanged,
}: {
  collection: CollectionDetail
  onChanged: (c: CollectionDetail) => void
}) {
  const [items, setItems] = useState<CollectionProductItem[]>(
    [...collection.products].sort((a, b) => a.displayOrder - b.displayOrder),
  )
  const [keyword, setKeyword] = useState('')
  const [results, setResults] = useState<ProductSummary[]>([])
  const [searching, setSearching] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dirty, setDirty] = useState(false)

  useEffect(() => {
    setItems([...collection.products].sort((a, b) => a.displayOrder - b.displayOrder))
    setDirty(false)
  }, [collection])

  const search = useCallback(async () => {
    setSearching(true)
    try {
      const page = await productApi.search({ keyword: keyword || undefined, size: 10 })
      setResults(page.content)
    } catch {
      setResults([])
    } finally {
      setSearching(false)
    }
  }, [keyword])

  const add = (product: ProductSummary) => {
    if (items.some((it) => it.product.id === product.id)) {
      toast.error('이미 편성된 상품입니다.')
      return
    }
    setItems((prev) => [...prev, { displayOrder: prev.length, product }])
    setDirty(true)
  }

  const remove = (productId: number) => {
    setItems((prev) => prev.filter((it) => it.product.id !== productId))
    setDirty(true)
  }

  const move = (index: number, dir: -1 | 1) => {
    setItems((prev) => {
      const next = [...prev]
      const target = index + dir
      if (target < 0 || target >= next.length) return prev
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
    setDirty(true)
  }

  const save = async () => {
    setSaving(true)
    setError(null)
    try {
      const updated = await adminCollectionApi.replaceProducts(
        collection.id,
        items.map((it) => it.product.id),
      )
      onChanged(updated)
      setDirty(false)
      toast.success('편성 상품을 저장했습니다.')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '저장 실패')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card>
      <CardContent className="space-y-3">
        <h2 className="font-bold">상품 편성</h2>

        <div className="flex gap-2">
          <Input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                search()
              }
            }}
            placeholder="상품명으로 검색"
            aria-label="상품명으로 검색"
          />
          <Button type="button" variant="outline" disabled={searching} onClick={search} className="shrink-0">
            {searching ? '검색 중…' : '검색'}
          </Button>
        </div>

        {results.length > 0 && (
          <ul className="max-h-48 space-y-1 overflow-y-auto rounded-lg border border-border p-2">
            {results.map((p) => (
              <li key={p.id} className="flex items-center justify-between gap-2 text-sm">
                <span className="truncate">
                  {p.name} <span className="text-xs text-muted-foreground">· {formatKRW(p.basePrice)}</span>
                </span>
                <Button
                  type="button"
                  variant="link"
                  size="sm"
                  className="h-auto shrink-0 p-0 text-xs"
                  onClick={() => add(p)}
                >
                  추가
                </Button>
              </li>
            ))}
          </ul>
        )}

        <div>
          <p className="mb-1 text-xs text-muted-foreground">편성된 상품 {items.length}개 (노출 순서, 위/아래로 조정)</p>
          {items.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">편성된 상품이 없습니다.</p>
          ) : (
            <ul className="space-y-1.5">
              {items.map((it, i) => (
                <li
                  key={it.product.id}
                  className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm"
                >
                  <span className="min-w-0 truncate">
                    <span className="mr-2 text-xs text-muted-foreground">#{i + 1}</span>
                    {it.product.name}
                  </span>
                  <div className="flex shrink-0 items-center gap-1">
                    <Button
                      type="button"
                      variant="outline"
                      size="icon-sm"
                      aria-label="위로 이동"
                      disabled={i === 0}
                      onClick={() => move(i, -1)}
                    >
                      ↑
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="icon-sm"
                      aria-label="아래로 이동"
                      disabled={i === items.length - 1}
                      onClick={() => move(i, 1)}
                    >
                      ↓
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="border-destructive/30 text-destructive hover:bg-destructive/10"
                      onClick={() => remove(it.product.id)}
                    >
                      삭제
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button type="button" disabled={saving || !dirty} onClick={save}>
          {saving ? '저장 중…' : '편성 저장'}
        </Button>
      </CardContent>
    </Card>
  )
}

export default function AdminCollectionEditPage() {
  const { id } = useParams<{ id: string }>()
  const isNew = !id
  const [collection, setCollection] = useState<CollectionDetail | null>(null)
  const [loading, setLoading] = useState(!isNew)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (isNew) return
    setLoading(true)
    adminCollectionApi
      .detail(Number(id))
      .then(setCollection)
      .catch((e) => setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.'))
      .finally(() => setLoading(false))
  }, [id, isNew])

  return (
    <div className="space-y-4">
      <Link to="/admin/collections" className="text-sm text-muted-foreground hover:underline">
        ← 기획전 목록
      </Link>
      <h1 className="text-lg font-bold">{isNew ? '기획전 등록' : '기획전 관리'}</h1>

      {loading ? (
        <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : (
        <>
          {collection && <StatusPanel collection={collection} onChanged={setCollection} />}
          <MetaForm collection={collection} onSaved={setCollection} />
          {collection && <ProductAssignmentPanel collection={collection} onChanged={setCollection} />}
        </>
      )}
    </div>
  )
}
