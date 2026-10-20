export const PASSWORD_REQUIREMENTS =
  'Mật khẩu phải có ít nhất 8 ký tự, chữ in hoa, chữ số và ý tự đặc biệt.'

export const PASSWORD_PATTERN =
  '(?=[\\s\\S]*\\p{Lu})(?=[\\s\\S]*\\p{Nd})(?=[\\s\\S]*[\\p{P}\\p{S}])[\\s\\S]{8,100}'

const passwordRegex = new RegExp(`^(?:${PASSWORD_PATTERN})(?![\\s\\S])`, 'u')

export function isValidPassword(password: string): boolean {
  return passwordRegex.test(password)
}
