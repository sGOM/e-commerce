export interface StockCsvRow {
  sku: string
  quantity: number
}

/**
 * 판매자 재고 일괄 수정용 CSV 파싱. `sku,quantity` 두 칸이면 충분해 라이브러리 없이 처리한다.
 * 첫 줄이 헤더(`sku,...`)면 건너뛰고, 빈 줄은 무시한다. 잘못된 줄은 줄 번호와 함께 예외.
 */
export function parseStockCsv(text: string): StockCsvRow[] {
  const rows: StockCsvRow[] = []
  text.split(/\r?\n/).forEach((line, i) => {
    const [rawSku = '', rawQty = ''] = line.split(',')
    const sku = rawSku.trim()
    if (!sku) return
    if (i === 0 && sku.toLowerCase() === 'sku') return // 헤더
    const quantity = Number(rawQty.trim())
    if (rawQty.trim() === '' || !Number.isInteger(quantity) || quantity < 0) {
      throw new Error(`${i + 1}번째 줄의 수량이 올바르지 않습니다: "${line.trim()}"`)
    }
    rows.push({ sku, quantity })
  })
  if (rows.length === 0) throw new Error('수정할 재고 줄이 없습니다.')
  return rows
}
