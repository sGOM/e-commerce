import { useCallback, useEffect, useState } from 'react'
import { qnaApi, sellerApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { Faq, Inquiry, SellerProduct } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Card } from '@/components/ui/card'

/** 판매자 상품 문의 답변 + 상품별 FAQ 관리(`docs/planning/product-qna.md` AC4·AC5). */
export default function SellerInquiriesPage() {
  const [onlyUnanswered, setOnlyUnanswered] = useState(true)
  const [inquiries, setInquiries] = useState<Inquiry[]>([])
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setInquiries(await qnaApi.sellerList(onlyUnanswered ? false : undefined))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '문의를 불러오지 못했습니다.')
    }
  }, [onlyUnanswered])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="space-y-8">
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-bold">상품 문의</h1>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={onlyUnanswered} onChange={(e) => setOnlyUnanswered(e.target.checked)} />
            답변 대기만 보기
          </label>
        </div>
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        {inquiries.length === 0 ? (
          <p className="text-sm text-muted-foreground">문의가 없습니다.</p>
        ) : (
          inquiries.map((q) => <InquiryCard key={q.inquiryId} inquiry={q} onAnswered={load} />)
        )}
      </section>
      <FaqManager />
    </div>
  )
}

function InquiryCard({ inquiry, onAnswered }: { inquiry: Inquiry; onAnswered: () => void }) {
  const [answer, setAnswer] = useState(inquiry.answer ?? '')
  const [error, setError] = useState<string | null>(null)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    try {
      await qnaApi.answer(inquiry.inquiryId, answer)
      onAnswered()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '답변을 등록하지 못했습니다.')
    }
  }

  return (
    <Card className="space-y-2 p-4 text-sm">
      <p className="text-xs text-muted-foreground">
        {inquiry.productName} · {new Date(inquiry.createdAt).toLocaleString('ko-KR')}
      </p>
      <p className="font-medium">Q. {inquiry.question}</p>
      <form onSubmit={submit} className="space-y-2">
        <Textarea
          required
          maxLength={2000}
          aria-label={`문의 ${inquiry.inquiryId} 답변`}
          value={answer}
          onChange={(e) => setAnswer(e.target.value)}
        />
        {error && (
          <p role="alert" className="text-destructive">
            {error}
          </p>
        )}
        <Button type="submit" size="sm">
          {inquiry.answer ? '답변 수정' : '답변 등록'}
        </Button>
      </form>
    </Card>
  )
}

function FaqManager() {
  const [products, setProducts] = useState<SellerProduct[]>([])
  const [productId, setProductId] = useState<number | null>(null)
  const [faqs, setFaqs] = useState<Faq[]>([])
  const [form, setForm] = useState({ question: '', answer: '', sortOrder: 0 })
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    sellerApi
      .myProducts()
      .then((ps) => {
        setProducts(ps)
        setProductId(ps[0]?.productId ?? null)
      })
      .catch(() => setProducts([]))
  }, [])

  const loadFaqs = useCallback(async () => {
    if (productId != null) setFaqs(await qnaApi.faqs(productId))
  }, [productId])

  useEffect(() => {
    loadFaqs()
  }, [loadFaqs])

  const run = async (action: () => Promise<unknown>) => {
    setError(null)
    try {
      await action()
      await loadFaqs()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '처리하지 못했습니다.')
    }
  }

  const add = (e: React.FormEvent) => {
    e.preventDefault()
    if (productId == null) return
    run(async () => {
      await qnaApi.addFaq(productId, form)
      setForm({ question: '', answer: '', sortOrder: 0 })
    })
  }

  return (
    <section className="space-y-4">
      <h2 className="text-xl font-bold">자주 묻는 질문(FAQ)</h2>
      {products.length === 0 ? (
        <p className="text-sm text-muted-foreground">상품을 먼저 등록해 주세요.</p>
      ) : (
        <>
          <select
            aria-label="FAQ 상품"
            className="h-9 rounded-lg border border-input bg-transparent px-2 text-sm"
            value={productId ?? ''}
            onChange={(e) => setProductId(Number(e.target.value))}
          >
            {products.map((p) => (
              <option key={p.productId} value={p.productId}>
                {p.name}
              </option>
            ))}
          </select>
          <ul className="space-y-2">
            {faqs.map((f) => (
              <li
                key={f.faqId}
                className="flex items-start justify-between gap-3 rounded-xl border border-border p-3 text-sm"
              >
                <div>
                  <p className="font-medium">Q. {f.question}</p>
                  <p className="text-muted-foreground">A. {f.answer}</p>
                </div>
                <Button type="button" size="sm" variant="outline" onClick={() => run(() => qnaApi.deleteFaq(f.faqId))}>
                  삭제
                </Button>
              </li>
            ))}
          </ul>
          <Card className="p-4">
            <form onSubmit={add} className="grid gap-2">
              <Input
                required
                maxLength={500}
                aria-label="FAQ 질문"
                placeholder="질문"
                value={form.question}
                onChange={(e) => setForm({ ...form, question: e.target.value })}
              />
              <Textarea
                required
                maxLength={2000}
                aria-label="FAQ 답변"
                placeholder="답변"
                value={form.answer}
                onChange={(e) => setForm({ ...form, answer: e.target.value })}
              />
              <Input
                type="number"
                aria-label="FAQ 순서"
                placeholder="순서(작을수록 위)"
                value={form.sortOrder}
                onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })}
              />
              {error && (
                <p role="alert" className="text-sm text-destructive">
                  {error}
                </p>
              )}
              <Button type="submit" className="justify-self-start">
                FAQ 추가
              </Button>
            </form>
          </Card>
        </>
      )}
    </section>
  )
}
