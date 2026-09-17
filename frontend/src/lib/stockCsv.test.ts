import { describe, expect, it } from 'vitest'
import { parseStockCsv } from './stockCsv'

describe('parseStockCsv', () => {
  it('헤더와 빈 줄을 건너뛰고 sku·수량을 뽑는다', () => {
    const csv = 'sku,quantity\nA-1,10\n\n B-2 , 0 \n'
    expect(parseStockCsv(csv)).toEqual([
      { sku: 'A-1', quantity: 10 },
      { sku: 'B-2', quantity: 0 },
    ])
  })

  it('수량이 숫자가 아니거나 음수면 줄 번호와 함께 알린다', () => {
    expect(() => parseStockCsv('A-1,열개')).toThrow('1번째 줄')
    expect(() => parseStockCsv('sku,quantity\nA-1,-1')).toThrow('2번째 줄')
  })

  it('내용이 없으면 거절한다', () => {
    expect(() => parseStockCsv('sku,quantity\n')).toThrow()
  })
})
