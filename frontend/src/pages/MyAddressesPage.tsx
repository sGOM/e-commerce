import { useCallback, useEffect, useState } from 'react'
import { addressApi, type AddressBody } from '../api/endpoints'
import { ApiError } from '../api/client'
import type { UserAddress } from '../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'

type AddressForm = Required<Omit<AddressBody, 'isDefault'>>

const emptyForm: AddressForm = {
  label: '',
  receiverName: '',
  receiverPhone: '',
  zipcode: '',
  address1: '',
  address2: '',
}

/** 마이페이지 배송지 주소록 — 목록·추가·수정·삭제·기본 배송지 지정. */
export default function MyAddressesPage() {
  const [addresses, setAddresses] = useState<UserAddress[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<number | 'new' | null>(null)
  const [form, setForm] = useState<AddressForm>(emptyForm)
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    try {
      setAddresses(await addressApi.list())
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  /** 요청 후 목록을 다시 불러온다. 성공 여부를 돌려줘 폼을 닫을지 결정한다. */
  const run = async (action: () => Promise<unknown>): Promise<boolean> => {
    setError(null)
    try {
      await action()
      await load()
      return true
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '요청을 처리하지 못했습니다.')
      return false
    }
  }

  const startEdit = (address: UserAddress | null) => {
    setEditingId(address ? address.addressId : 'new')
    setForm(
      address
        ? {
            label: address.label ?? '',
            receiverName: address.receiverName,
            receiverPhone: address.receiverPhone,
            zipcode: address.zipcode,
            address1: address.address1,
            address2: address.address2 ?? '',
          }
        : emptyForm,
    )
  }

  const set = (key: keyof AddressForm) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (editingId === null) return
    setSaving(true)
    const body: AddressBody = { ...form, label: form.label || undefined, address2: form.address2 || undefined }
    const ok = await run(() =>
      editingId === 'new' ? addressApi.create(body) : addressApi.update(editingId, body),
    )
    setSaving(false)
    if (ok) setEditingId(null)
  }

  if (loading) return <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">배송지 관리</h1>
        {editingId === null && <Button onClick={() => startEdit(null)}>+ 배송지 추가</Button>}
      </div>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      {editingId !== null && (
        <Card className="p-5">
          <form onSubmit={save} className="grid gap-3 sm:grid-cols-2">
            <Input
              placeholder="배송지 이름 (예: 집, 회사)"
              aria-label="배송지 이름"
              maxLength={50}
              value={form.label}
              onChange={set('label')}
              className="sm:col-span-2"
            />
            <Input required placeholder="받는 분" aria-label="받는 분" value={form.receiverName} onChange={set('receiverName')} />
            <Input required placeholder="받는 분 연락처" aria-label="받는 분 연락처" value={form.receiverPhone} onChange={set('receiverPhone')} />
            <Input required placeholder="우편번호" aria-label="우편번호" value={form.zipcode} onChange={set('zipcode')} />
            <Input required placeholder="기본 주소" aria-label="기본 주소" value={form.address1} onChange={set('address1')} />
            <Input
              placeholder="상세 주소 (선택)"
              aria-label="상세 주소 (선택)"
              value={form.address2}
              onChange={set('address2')}
              className="sm:col-span-2"
            />
            <div className="flex justify-end gap-2 sm:col-span-2">
              <Button type="button" variant="outline" onClick={() => setEditingId(null)}>
                취소
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? '저장 중…' : '저장'}
              </Button>
            </div>
          </form>
        </Card>
      )}

      {addresses.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">등록된 배송지가 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {addresses.map((a) => (
            <li key={a.addressId}>
              <Card className="p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="text-sm">
                    <p className="font-medium">
                      {a.label ?? a.receiverName}
                      {a.isDefault && (
                        <span className="ml-2 rounded bg-primary/10 px-2 py-0.5 text-xs text-primary">
                          기본 배송지
                        </span>
                      )}
                    </p>
                    <p className="text-muted-foreground">
                      {a.receiverName} · {a.receiverPhone}
                    </p>
                    <p className="text-muted-foreground">
                      ({a.zipcode}) {a.address1} {a.address2 ?? ''}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    {!a.isDefault && (
                      <Button size="sm" variant="outline" onClick={() => run(() => addressApi.setDefault(a.addressId))}>
                        기본으로
                      </Button>
                    )}
                    <Button size="sm" variant="outline" onClick={() => startEdit(a)}>
                      수정
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => run(() => addressApi.remove(a.addressId))}>
                      삭제
                    </Button>
                  </div>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
