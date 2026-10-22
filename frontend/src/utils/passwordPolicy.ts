export const PASSWORD_HINT =
  'Mật khẩu phải có ít nhất 8 ký tự, chữ in hoa, chữ số và ký tự đặc biệt.'

export const PASSWORD_REQUIREMENTS =
  'Mật khẩu phải có ít nhất 8 ký tự, chữ in hoa, chữ số và ký tự đặc biệt, tối đa 72 byte UTF-8 (tiếng Việt có dấu/emoji chiếm nhiều byte hơn).'

export const PASSWORD_PATTERN =
  '(?=[\\s\\S]*\\p{Lu})(?=[\\s\\S]*\\p{Nd})(?=[\\s\\S]*[\\p{P}\\p{S}])[\\s\\S]{8,100}'

const passwordRegex = new RegExp(`^(?:${PASSWORD_PATTERN})(?![\\s\\S])`, 'u')

export function isValidPassword(password: string): boolean {
  return new TextEncoder().encode(password).length <= 72 && passwordRegex.test(password)
}
