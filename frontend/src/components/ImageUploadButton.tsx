import { useId, useState } from 'react'
import { uploadApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/** 이미지 파일을 골라 `/api/uploads` 로 올리고 결과 URL 을 넘겨주는 버튼(상품 대표 이미지·리뷰 사진 공용). */
export default function ImageUploadButton({
  label,
  onUploaded,
  disabled,
}: {
  label: string
  onUploaded: (url: string) => void
  disabled?: boolean
}) {
  const id = useId()
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const upload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = '' // 같은 파일을 다시 골라도 change 가 일어나도록
    if (!file) return
    setUploading(true)
    setError(null)
    try {
      onUploaded((await uploadApi.image(file)).url)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '업로드에 실패했습니다.')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="space-y-1">
      <label
        htmlFor={id}
        className={cn(
          buttonVariants({ variant: 'outline', size: 'sm' }),
          (disabled || uploading) && 'pointer-events-none opacity-50',
        )}
      >
        {uploading ? '업로드 중…' : label}
      </label>
      <input
        id={id}
        type="file"
        accept="image/jpeg,image/png,image/gif,image/webp"
        aria-label={label}
        className="sr-only"
        disabled={disabled || uploading}
        onChange={upload}
      />
      {error && (
        <p role="alert" className="text-xs text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
