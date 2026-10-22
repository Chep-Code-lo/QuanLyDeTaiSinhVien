// Optional browser audit. Every API request is intercepted; no real account or database is used.
// Set AUDIT_PLAYWRIGHT_MODULE to a Playwright index.mjs and run Vite on localhost:5188.
import { mkdir, writeFile } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'
import path from 'node:path'
const { chromium } = await import(pathToFileURL(process.env.AUDIT_PLAYWRIGHT_MODULE).href)
const artifacts = process.env.AUDIT_ARTIFACTS_DIR
if (!artifacts) throw new Error('Set AUDIT_ARTIFACTS_DIR to a temporary output directory.')
await mkdir(artifacts, { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.AUDIT_BROWSER_PATH, headless: true })
const pageData = content => ({ content, totalPages: content.length ? 1 : 0, totalElements: content.length, page: 0, size: 10 })
const period = { id: 1, facultyId: 1, facultyName: 'Khoa CNTT', semesterId: 1, name: 'Đợt kiểm thử', projectType: 'COURSE', status: 'ACTIVE',
  minGroupMembers: 1, maxGroupMembers: 3, minCommitteeMembers: 3, maxCommitteeMembers: 5, invitationExpiryHours: 48,
  proposalStartAt: '2099-01-01T00:00:00Z', proposalEndAt: '2099-01-02T00:00:00Z', registrationStartAt: '2099-01-03T00:00:00Z', registrationEndAt: '2099-01-04T00:00:00Z' }
const roles = {
  ADMIN: ['/dashboard', '/topics', '/groups', '/registrations', '/progress', '/evaluations', '/evaluator-assignment', '/academic-management', '/admin-appointments', '/admin-users', '/email-settings', '/announcements', '/account'],
  LECTURER: ['/score-sheets', '/progress'],
  DEAN: ['/academic-management', '/evaluations', '/evaluator-assignment'],
  DEPARTMENT_HEAD: ['/academic-management', '/topics'],
  STUDENT: ['/groups', '/registrations', '/progress', '/results', '/account'],
  GUEST: ['/login'],
}
const results = []
try {
  for (const [role, routes] of Object.entries(roles)) {
    const context = await browser.newContext()
    const page = await context.newPage()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.route('**/api/v1/**', async route => {
      const url = new URL(route.request().url())
      const resource = url.pathname.replace('/api/v1', '')
      let data = pageData([])
      let status = 200
      if (resource === '/auth/me') {
        if (role === 'GUEST') status = 401
        const academicRole = role === 'DEAN' || role === 'DEPARTMENT_HEAD'
        data = { id: 1, fullName: 'Người dùng kiểm thử', username: 'audit@example.test', roles: [academicRole ? 'LECTURER' : role],
          activeAppointments: academicRole ? [{ id: 1, positionCode: role, facultyId: 1, facultyName: 'Khoa CNTT', departmentId: role === 'DEPARTMENT_HEAD' ? 1 : null, departmentName: 'Bộ môn', startsAt: '2000-01-01T00:00:00Z' }] : [],
          lecturer: role === 'LECTURER' || academicRole ? { id: 1, lecturerCode: 'GV01' } : null, student: role === 'STUDENT' ? { id: 1, studentCode: 'SV01' } : null }
      } else if (resource === '/dashboard') data = { totalTopics: 0, registeringTopics: 0, inProgressTopics: 0, completedTopics: 0, unreadNotifications: 0, openRegistrationPeriods: 0,
        topicTypes: [], recentTopics: [], upcomingDeadlines: [], currentSemester: null, updatedAt: '2099-01-01T00:00:00Z' }
      else if (resource === '/notifications/unread-count') data = { unreadCount: 0 }
      else if (resource === '/registration-periods') data = pageData([period])
      else if (resource === '/notification-settings') data = [{ id: 1, eventType: 'TASK_ASSIGNED', scopeType: 'SYSTEM', scopeId: 0,
        enabled: false, senderName: 'Thông báo', fromEmail: 'audit@example.test', subjectTemplate: 'Nhiệm vụ mới', bodyTemplate: 'Nội dung kiểm thử', recipientGroups: ['DIRECT_PARTICIPANTS'], allowedVariables: [], version: 0 }]
      else if (['/faculties', '/departments', '/semesters', '/admin/academic-appointments', '/assessment-components', '/rubrics', '/notification-settings', '/groups/mine', '/project-assignments/mine', '/student/defense-sessions', '/student/results'].includes(resource) || resource.includes('/milestones')) data = []
      if (route.request().method() !== 'GET') throw new Error('Browser audit must not mutate application data.')
      await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ success: status === 200, data }) })
    })
    for (const width of [390, 768, 1440]) {
      await page.setViewportSize({ width, height: 900 })
      for (const route of routes) {
        const before = errors.length
        await page.goto('http://127.0.0.1:5188' + route, { waitUntil: 'networkidle' })
        const headings = await page.locator('h1').allTextContents()
        const dimensions = await page.evaluate(() => ({ viewport: window.innerWidth, body: document.documentElement.scrollWidth }))
        const row = { role, width, route, headings, dimensions, errors: errors.slice(before) }
        row.passed = row.errors.length === 0 && dimensions.body <= width + 1 && headings.length > 0 && new URL(page.url()).pathname === route
        if (!row.passed) {
          row.overflow = await page.locator('body *').evaluateAll(elements => elements.filter(element => {
            const bounds = element.getBoundingClientRect(); return bounds.right > window.innerWidth + 1 && bounds.width > 0
          }).slice(0, 10).map(element => ({ tag: element.tagName, className: element.className })))
          await page.screenshot({ path: path.join(artifacts, `${role}-${width}-${route.slice(1)}.png`), fullPage: true })
        }
        results.push(row)
      }
    }
    await context.close()
  }
} finally { await browser.close() }
await writeFile(path.join(artifacts, 'results.json'), JSON.stringify(results, null, 2))
console.log(JSON.stringify({ checks: results.length, failed: results.filter(result => !result.passed) }, null, 2))
if (results.some(result => !result.passed)) process.exitCode = 1
