import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { setTimeout } from 'node:timers/promises'
import test from 'node:test'

const docker = (...args) =>
  execFileSync('docker', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()

for (const mode of ['direct', 'CF-Connecting-IP', 'X-Forwarded-For']) {
  test(`Nginx trusts only the configured peer in ${mode} mode`, { timeout: 60_000 }, async () => {
    const prefix = `qldetai-proxy-test-${process.pid}-${mode.toLowerCase()}`
    const network = `${prefix}-net`
    const upstream = `${prefix}-upstream`
    const proxy = `${prefix}-nginx`
    const config = fileURLToPath(new URL('../nginx.conf', import.meta.url))
    const entrypoint = fileURLToPath(
      new URL('../docker-entrypoint.d/25-trusted-proxies.sh', import.meta.url),
    )
    const server = `require('http').createServer((req,res)=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(req.headers))}).listen(8080,'0.0.0.0')`
    docker('network', 'create', network)
    try {
      docker(
        'run',
        '-d',
        '--name',
        upstream,
        '--network',
        network,
        '--network-alias',
        'backend',
        'node:22-alpine',
        'node',
        '-e',
        server,
      )
      const peer = docker(
        'inspect',
        upstream,
        '--format',
        '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}',
      )
      docker(
        'run',
        '-d',
        '--name',
        proxy,
        '--network',
        network,
        '--network-alias',
        'proxy',
        '-p',
        '127.0.0.1::80',
        '-e',
        `NGINX_TRUSTED_PROXY_CIDRS=${mode === 'direct' ? '' : peer + '/32'}`,
        '-e',
        `NGINX_REAL_IP_HEADER=${mode === 'direct' ? 'CF-Connecting-IP' : mode}`,
        '-v',
        `${config}:/etc/nginx/conf.d/default.conf:ro`,
        '-v',
        `${entrypoint}:/trusted-proxies.sh:ro`,
        '--entrypoint',
        'sh',
        process.env.NGINX_TEST_IMAGE ?? 'nginx:1.29-alpine',
        '-c',
        `sh /trusted-proxies.sh && exec nginx -g 'daemon off;'`,
      )
      const address = docker('port', proxy, '80/tcp').split('\n')[0]
      let first
      for (let attempt = 0; attempt < 100; attempt++) {
        try {
          const response = await fetch(`http://${address}/api/probe`)
          if (response.ok) {
            first = await response.json()
            break
          }
        } catch {
          /* Wait for container startup. */
        }
        await setTimeout(100)
      }
      assert.ok(first, 'Nginx and the isolated upstream must become ready')
      for (let index = 0; index < 6; index++) {
        const response = await fetch(`http://${address}/api/probe`, {
          headers: {
            'X-Forwarded-For': `203.0.113.${index + 1}`,
            'X-Real-IP': '198.51.100.99',
            'CF-Connecting-IP': '198.51.100.100',
            Forwarded: 'for=198.51.100.101',
            'X-Forwarded-Proto': 'https',
          },
        })
        assert.equal(response.status, 200)
        const headers = await response.json()
        assert.equal(headers['x-forwarded-for'], first['x-forwarded-for'])
        assert.equal(headers['x-real-ip'], first['x-forwarded-for'])
        assert.equal(headers.forwarded, undefined)
        assert.equal(headers['x-forwarded-proto'], 'http')
        assert.ok(!headers['x-forwarded-for'].includes('203.0.113.'))
      }
      if (mode !== 'direct') {
        const probe = `fetch('http://proxy/api/probe',{headers:{'${mode}':'203.0.113.42','X-Forwarded-Proto':'https'}}).then(r=>r.text()).then(console.log)`
        const forwarded = JSON.parse(docker('exec', upstream, 'node', '-e', probe))
        assert.equal(forwarded['x-forwarded-for'], '203.0.113.42')
        assert.equal(forwarded['x-forwarded-proto'], 'https')
      }
    } finally {
      for (const name of [proxy, upstream]) {
        try {
          docker('rm', '-f', name)
        } catch {
          /* Container may not have started. */
        }
      }
      docker('network', 'rm', network)
    }
  })
}
