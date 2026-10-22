import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { setTimeout } from 'node:timers/promises'
import test from 'node:test'

const docker = (...args) => execFileSync('docker', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()

test('Nginx replaces forged forwarding headers with the connection address', { timeout: 60_000 }, async () => {
  const prefix = `qldetai-proxy-test-${process.pid}`
  const network = `${prefix}-net`
  const upstream = `${prefix}-upstream`
  const proxy = `${prefix}-nginx`
  const config = fileURLToPath(new URL('../nginx.conf', import.meta.url))
  const server = `require('http').createServer((req,res)=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(req.headers))}).listen(8080,'0.0.0.0')`
  docker('network', 'create', network)
  try {
    docker('run', '-d', '--name', upstream, '--network', network, '--network-alias', 'backend',
      'node:22-alpine', 'node', '-e', server)
    docker('run', '-d', '--name', proxy, '--network', network, '-p', '127.0.0.1::80',
      '-v', `${config}:/etc/nginx/conf.d/default.conf:ro`, process.env.NGINX_TEST_IMAGE ?? 'nginx:1.29-alpine')
    const address = docker('port', proxy, '80/tcp').split('\n')[0]
    let first
    for (let attempt = 0; attempt < 100; attempt++) {
      try {
        const response = await fetch(`http://${address}/api/probe`)
        if (response.ok) { first = await response.json(); break }
      } catch { /* Wait for container startup. */ }
      await setTimeout(100)
    }
    assert.ok(first, 'Nginx and the isolated upstream must become ready')
    for (let index = 0; index < 6; index++) {
      const response = await fetch(`http://${address}/api/probe`, { headers: {
        'X-Forwarded-For': `203.0.113.${index + 1}`, 'X-Real-IP': '198.51.100.99',
        'CF-Connecting-IP': '198.51.100.100', 'Forwarded': 'for=198.51.100.101',
      } })
      assert.equal(response.status, 200)
      const headers = await response.json()
      assert.equal(headers['x-forwarded-for'], first['x-forwarded-for'])
      assert.equal(headers['x-real-ip'], first['x-forwarded-for'])
      assert.equal(headers.forwarded, undefined)
      assert.ok(!headers['x-forwarded-for'].includes('203.0.113.'))
    }
  } finally {
    for (const name of [proxy, upstream]) {
      try { docker('rm', '-f', name) } catch { /* Container may not have started. */ }
    }
    docker('network', 'rm', network)
  }
})
