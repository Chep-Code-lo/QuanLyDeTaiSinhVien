import type { CurrentUser } from '../types/domain'

export function avatarInitial(fullName?: string): string {
  const givenName = fullName?.trim().split(/\s+/).at(-1)
  return givenName ? Array.from(givenName.normalize('NFC'))[0].toLocaleUpperCase('vi-VN') : 'U'
}

export function greetingName(user: CurrentUser | null): string {
  const name = user?.fullName?.trim() || 'Người dùng'
  const degree = user?.lecturer?.academicDegree?.trim()
  const degreeKey = degree
    ?.normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/[.\s]/g, '')
    .toLowerCase()
  const degrees: Record<string, string> = {
    ts: 'TS',
    tiensi: 'TS',
    phd: 'TS',
    ths: 'Th.S',
    thacsi: 'Th.S',
    msc: 'Th.S',
    pgs: 'PGS.TS',
    phogiaosu: 'PGS.TS',
    pgsts: 'PGS.TS',
    gs: 'GS',
    giaosu: 'GS',
  }
  const prefix = degreeKey ? degrees[degreeKey] || degree : degree
  return user?.roles.includes('LECTURER') && prefix ? `${prefix} ${name}` : name
}
