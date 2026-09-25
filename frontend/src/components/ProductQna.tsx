import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { qnaApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { Faq, Inquiry } from '../api/types'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

/**
 * 상품 상세의 Q&A(`docs/planning/product-qna.md`). FAQ 는 누구나 보고, 문의는 비밀이라 로그인한 본인 것만 보인다.
 */
export default function ProductQna({ productId }: { productId: number }) {
  const { user } = useAuth()
  const [faqs, setFaqs] = useState<Faq[]>([])
  const [mine, setMine] = useState<Inquiry[]>([])
  const [question, setQuestion] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    qnaApi
      .faqs(productId)
      .then(setFaqs)
      .catch(() => setFaqs([]))
  }, [productId])

  useEffect(() => {
    if (!user) return
    qnaApi
      .mine(productId)
      .then(setMine)
      .catch(() => setMine([]))
  }, [productId, user])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const created = await qnaApi.ask(productId, question)
      setMine((prev) => [created, ...prev])
      setQuestion('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '문의를 등록하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="space-y-6">
      <div>
        <h2 className="mb-3 text-lg font-bold">자주 묻는 질문</h2>
        {faqs.length === 0 ? (
          <p className="text-sm text-muted-foreground">등록된 질문이 없습니다.</p>
        ) : (
          <ul className="divide-y divide-border rounded-xl border border-border">
            {faqs.map((f) => (
              <li key={f.faqId}>
                <details className="group p-4">
                  <summary className="cursor-pointer font-medium">Q. {f.question}</summary>
                  <p className="mt-2 text-sm whitespace-pre-line text-muted-foreground">A. {f.answer}</p>
                </details>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <h2 className="mb-1 text-lg font-bold">상품 문의</h2>
        <p className="mb-3 text-xs text-muted-foreground">
          🔒 문의는 비밀글로 등록되어 작성자와 판매자만 볼 수 있습니다.
        </p>
        {!user ? (
          <p className="text-sm text-muted-foreground">
            문의하려면{' '}
            <Link to="/login" className="underline">
              로그인
            </Link>
            해 주세요.
          </p>
        ) : (
          <>
            <form onSubmit={submit} className="space-y-2">
              <Textarea
                required
                maxLength={1000}
                aria-label="문의 내용"
                placeholder="궁금한 점을 적어 주세요(최대 1000자)"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
              />
              {error && (
                <p role="alert" className="text-sm text-destructive">
                  {error}
                </p>
              )}
              <Button type="submit" disabled={submitting || !question.trim()}>
                {submitting ? '등록 중…' : '문의하기'}
              </Button>
            </form>
            {mine.length > 0 && (
              <ul className="mt-4 space-y-3">
                {mine.map((q) => (
                  <li key={q.inquiryId} className="rounded-xl border border-border p-4 text-sm">
                    <p className="font-medium">Q. {q.question}</p>
                    {q.answer ? (
                      <p className="mt-2 whitespace-pre-line text-muted-foreground">A. {q.answer}</p>
                    ) : (
                      <p className="mt-2 text-xs text-warning">답변 대기 중</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </div>
    </section>
  )
}
