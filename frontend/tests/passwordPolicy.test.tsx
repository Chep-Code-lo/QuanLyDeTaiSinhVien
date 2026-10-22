import { expect, it } from 'vitest'
import { isValidPassword, PASSWORD_REQUIREMENTS } from '../src/utils/passwordPolicy'

it.each([
  ['Abcdef1!', true], ['abcdefgh1!', false], ['Abcdefgh!', false], ['Abcdefg1', false],
  ['Abc1!', false], ['ĐồÁn2026!', true], ['ĐềTài١٢!', true], ['Abcdef1😀', true],
  ['A1!' + 'a'.repeat(97), false], ['A1!' + 'a'.repeat(69), true],
  ['A1!' + 'a'.repeat(70), false], ['A1!' + 'ề'.repeat(23), true],
  ['A1!' + 'ề'.repeat(23) + 'a', false], ['A1!' + '😀'.repeat(17) + 'a', true],
  ['A1!' + '😀'.repeat(17) + 'ab', false], ['A1!' + 'a'.repeat(98), false],
])('kiểm tra mật khẩu và các nhóm ký tự Unicode: %s', (password, valid) => {
  expect(isValidPassword(String(password))).toBe(valid)
})

it('hiển thị đúng chính tả yêu cầu ký tự đặc biệt', () => {
  expect(PASSWORD_REQUIREMENTS).toContain('ký tự đặc biệt')
})
