import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { runInNewContext } from 'node:vm'
import { JSDOM } from 'jsdom'
import { describe, expect, it, vi } from 'vitest'

const resources = resolve(process.cwd(), '../src/main/resources')
const contextScript = readFileSync(resolve(resources, 'static/login-context.js'), 'utf8')
function loginPage(search = '', checked = false, runScript = true) {
  const dom = new JSDOM(
    `<form class="halo-form" id="login-form">
      <input type="checkbox" id="remember-me" ${checked ? 'checked' : ''}>
      <button type="submit">登录</button>
    </form>
    <div id="passkey-error" style="display: none"></div>
    <div id="passkey-warn" style="display: none"></div>
    <ul class="pill-items">
      <li><a id="passkey" href="/login?method=passkey"><span>通行密钥</span></a></li>
      <li><a id="password" href="/login?method=local">密码</a></li>
      <li><a id="external" href="https://example.org/login?method=passkey">外部链接</a></li>
    </ul>`,
    { url: 'https://halo.example/login' + search, runScripts: 'outside-only' },
  )
  if (runScript) {
    dom.window.eval(contextScript)
    dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'))
  }
  return dom
}

function linkUrl(dom: JSDOM, id = 'passkey') {
  const link = dom.window.document.getElementById(id) as HTMLAnchorElement
  return new URL(link.href)
}

describe('登录方式切换', () => {
  it('在没有登录表单的页面不修改链接', () => {
    const dom = new JSDOM('<a href="/login?method=passkey">登录</a>', {
      url: 'https://halo.example/post?redirect_uri=%2Fuc',
      runScripts: 'outside-only',
    })
    dom.window.eval(contextScript)
    dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'))
    expect(dom.window.document.querySelector('a')!.getAttribute('href')).toBe(
      '/login?method=passkey',
    )
    dom.window.close()
  })

  it('完整保留嵌套的 OIDC 参数、重复参数和编码', () => {
    const redirect =
      '/oidc/authorize?client_id=demo&scope=openid+profile&state=a%2Bb%25%26' +
      '&nonce=中文&code_challenge=A+B%2F'
    const params = new URLSearchParams({ redirect_uri: redirect, method: 'local' })
    params.append('custom', 'one')
    params.append('custom', 'two')
    const dom = loginPage('?' + params.toString())
    const target = linkUrl(dom)

    expect(target.searchParams.get('method')).toBe('passkey')
    expect(target.searchParams.get('redirect_uri')).toBe(redirect)
    expect(target.searchParams.getAll('custom')).toEqual(['one', 'two'])
    expect(target.searchParams.getAll('method')).toEqual(['passkey'])
    expect(linkUrl(dom, 'external').href).toBe('https://example.org/login?method=passkey')
    dom.window.close()
  })

  it.each([true, false])('点击时传递最新的勾选状态 %s', (checked) => {
    const dom = loginPage('?redirect_uri=%2Fuc')
    const rememberMe = dom.window.document.getElementById('remember-me') as HTMLInputElement
    rememberMe.checked = checked
    const span = dom.window.document.querySelector('#passkey span')!
    span.dispatchEvent(new dom.window.MouseEvent('click', { bubbles: true }))

    expect(linkUrl(dom).searchParams.get('remember-me')).toBe(String(checked))
    expect(linkUrl(dom).searchParams.get('redirect_uri')).toBe('/uc')
    dom.window.close()
  })

  it.each([true, false])('通过参数恢复勾选状态 %s，并支持切回密码登录', (checked) => {
    const dom = loginPage('?method=passkey&remember-me=' + checked, !checked)
    const rememberMe = dom.window.document.getElementById('remember-me') as HTMLInputElement

    expect(rememberMe.checked).toBe(checked)
    const target = linkUrl(dom, 'password')
    expect(target.searchParams.get('method')).toBe('local')
    expect(target.searchParams.get('remember-me')).toBe(String(checked))
    dom.window.close()
  })

  it('未提供参数时沿用 Halo 的勾选状态，变更后同步可新标签打开的链接', () => {
    const dom = loginPage('', true)
    const rememberMe = dom.window.document.getElementById('remember-me') as HTMLInputElement

    expect(rememberMe.checked).toBe(true)
    expect(linkUrl(dom).searchParams.get('remember-me')).toBe('true')
    rememberMe.checked = false
    rememberMe.dispatchEvent(new dom.window.Event('change'))
    expect(linkUrl(dom).searchParams.get('remember-me')).toBe('false')
    dom.window.close()
  })
})

describe('通行密钥认证', () => {
  it.each([true, false])('验证请求显式携带 remember-me=%s 并返回原始授权地址', async (checked) => {
    const redirect = '/oidc/authorize?client_id=demo&state=a%2Bb&scope=openid+profile'
    const dom = loginPage(
      '?' + new URLSearchParams({ redirect_uri: redirect }).toString(),
      checked,
      false,
    )
    const fetch = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ challenge: 'AQID', rpId: 'halo.example', sessionId: 'session' }),
      })
      .mockResolvedValueOnce({ ok: true })
    const getCredential = vi.fn().mockResolvedValue({
      rawId: new Uint8Array([1, 2, 3]).buffer,
      response: {
        authenticatorData: new Uint8Array([1]).buffer,
        clientDataJSON: new Uint8Array([2]).buffer,
        signature: new Uint8Array([3]).buffer,
        userHandle: null,
      },
    })
    const location = {
      origin: 'https://halo.example',
      search: dom.window.location.search,
      href: 'https://halo.example/login' + dom.window.location.search,
    }
    runInNewContext(contextScript, {
      window: {
        location,
        PublicKeyCredential: function () {},
        addEventListener: vi.fn(),
        URL,
      },
      document: dom.window.document,
      navigator: { credentials: { get: getCredential } },
      fetch,
      URL,
      URLSearchParams,
      Uint8Array,
      atob,
      btoa,
    })
    dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'))
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(1))
    const link = dom.window.document.querySelector('#passkey') as HTMLAnchorElement
    link.click()

    await vi.waitFor(() => expect(location.href).toBe(redirect))
    expect(location.href).not.toContain('method=passkey')
    expect(fetch).toHaveBeenCalledTimes(2)
    expect(fetch.mock.calls[1][0]).toBe(
      '/apis/api.passkey.halo.run/v1alpha1/authentication/verify?remember-me=' + checked,
    )
    expect(getCredential).toHaveBeenCalledOnce()
    dom.window.close()
  })
})
