// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ProductQna from './ProductQna'
import { qnaApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'

vi.mock('../api/endpoints', () => ({ qnaApi: { faqs: vi.fn(), mine: vi.fn(), ask: vi.fn() } }))
vi.mock('../auth/AuthContext', () => ({ useAuth: vi.fn() }))

const renderQna = () =>
  render(
    <MemoryRouter>
      <ProductQna productId={7} />
    </MemoryRouter>,
  )

describe('ProductQna', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('FAQ 는 누구나 보고, 게스트에게는 문의 대신 로그인 안내가 보인다', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: null } as never)
    vi.mocked(qnaApi.faqs).mockResolvedValue([{ faqId: 1, question: '배송 기간은?', answer: '2일', sortOrder: 0 }])
    renderQna()

    expect(await screen.findByText('Q. 배송 기간은?')).toBeTruthy()
    expect(screen.getByRole('link', { name: '로그인' })).toBeTruthy()
    expect(qnaApi.mine).not.toHaveBeenCalled()
  })

  it('회원이 문의하면 내 문의 목록 맨 위에 답변 대기로 추가된다', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { name: '회원' } } as never)
    vi.mocked(qnaApi.faqs).mockResolvedValue([])
    vi.mocked(qnaApi.mine).mockResolvedValue([])
    vi.mocked(qnaApi.ask).mockResolvedValue({
      inquiryId: 3,
      productId: 7,
      productName: '상품',
      question: '정사이즈인가요?',
      answer: null,
      answeredAt: null,
      createdAt: '2026-09-25T00:00:00Z',
    })
    renderQna()

    fireEvent.change(await screen.findByLabelText('문의 내용'), { target: { value: '정사이즈인가요?' } })
    fireEvent.click(screen.getByRole('button', { name: '문의하기' }))

    expect(await screen.findByText('Q. 정사이즈인가요?')).toBeTruthy()
    expect(screen.getByText('답변 대기 중')).toBeTruthy()
    expect(qnaApi.ask).toHaveBeenCalledWith(7, '정사이즈인가요?')
  })
})
