// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import ImageUploadButton from './ImageUploadButton'
import { uploadApi } from '../api/endpoints'
import { ApiError } from '../api/client'

vi.mock('../api/endpoints', () => ({ uploadApi: { image: vi.fn() } }))

const file = new File([new Uint8Array([0x89, 0x50])], 'a.png', { type: 'image/png' })

describe('ImageUploadButton', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('파일을 고르면 업로드하고 URL 을 넘겨준다', async () => {
    vi.mocked(uploadApi.image).mockResolvedValue({ url: '/api/uploads/x.png' })
    const onUploaded = vi.fn()
    render(<ImageUploadButton label="사진 추가" onUploaded={onUploaded} />)

    fireEvent.change(screen.getByLabelText('사진 추가'), { target: { files: [file] } })

    await waitFor(() => expect(onUploaded).toHaveBeenCalledWith('/api/uploads/x.png'))
    expect(uploadApi.image).toHaveBeenCalledWith(file)
  })

  it('업로드가 거부되면 서버 메시지를 보여준다', async () => {
    vi.mocked(uploadApi.image).mockRejectedValue(new ApiError('이미지만 업로드할 수 있습니다.', 'UPLOAD-001', 400))
    const onUploaded = vi.fn()
    render(<ImageUploadButton label="사진 추가" onUploaded={onUploaded} />)

    fireEvent.change(screen.getByLabelText('사진 추가'), { target: { files: [file] } })

    expect((await screen.findByRole('alert')).textContent).toBe('이미지만 업로드할 수 있습니다.')
    expect(onUploaded).not.toHaveBeenCalled()
  })
})
