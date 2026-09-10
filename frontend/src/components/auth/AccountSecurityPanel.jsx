import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import apiClient from '../../api/apiClient'
import GoogleSignInButton from './GoogleSignInButton'
import PasswordField from './PasswordField'
import getApiErrorMessage from '../../utils/getApiErrorMessage'

export default function AccountSecurityPanel({ profile }) {
  const navigate = useNavigate()
  const [username, setUsername] = useState(profile.username)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [googleLinked, setGoogleLinked] = useState(false)
  const [credential, setCredential] = useState('')
  const [method, setMethod] = useState('password')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [loadingMethods, setLoadingMethods] = useState(true)
  const [methodsFailed, setMethodsFailed] = useState(false)
  const [reloadMethods, setReloadMethods] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setLoadingMethods(true)
    setMethodsFailed(false)
    apiClient.get('/users/me/security', { signal: controller.signal })
      .then(({ data }) => setGoogleLinked(data.data.googleLinked))
      .catch((err) => { if (err.code !== 'ERR_CANCELED') setMethodsFailed(true) })
      .finally(() => { if (!controller.signal.aborted) setLoadingMethods(false) })
    return () => controller.abort()
  }, [reloadMethods])
  useEffect(() => {
    if (!credential) return undefined
    const timer = window.setTimeout(() => {
      setCredential('')
      setError('Google verification expired. Verify again before saving.')
    }, 5 * 60 * 1000)
    return () => window.clearTimeout(timer)
  }, [credential])
  async function submit(event) {
    event.preventDefault()
    if (busy || loadingMethods || methodsFailed) return
    setError('')
    if (newPassword && (Array.from(newPassword).length < 15 || new TextEncoder().encode(newPassword).length > 72)) {
      setError('Use at least 15 characters and at most 72 UTF-8 bytes.'); return
    }
    if (newPassword !== confirmPassword) { setError('Passwords do not match.'); return }
    setBusy(true)
    try {
      await apiClient.post('/users/me/security', {
        username: username === profile.username ? null : username,
        newPassword: newPassword || null, confirmPassword: confirmPassword || null,
        currentPassword: method === 'password' ? currentPassword : null,
        googleCredential: method === 'google' ? credential : null,
      })
      localStorage.removeItem('accessToken')
      sessionStorage.setItem('securityUpdated', 'true')
      navigate('/login', { replace: true })
    } catch (err) { setCredential(''); setError(getApiErrorMessage(err, 'Unable to update account security.')) }
    finally { setBusy(false) }
  }
  return <section className="profile-panel profile-panel--security">
    <div className="section-heading"><div><p className="eyebrow">Account protection</p><h2>Sign-in and password</h2></div></div>
    <p className="field-help">Confirm your identity before changing login details. Saving signs out all devices, including this one.</p>
    <form className="profile-form" onSubmit={submit}>
      {loadingMethods && <p role="status">Loading verification methods…</p>}
      {methodsFailed && <p role="alert">Unable to load verification methods. <button className="text-button" type="button" onClick={() => setReloadMethods((key) => key + 1)}>Try again</button></p>}
      <fieldset disabled={busy || loadingMethods || methodsFailed} className="security-fields">
      <div className="field-group"><label htmlFor="security-username">Username</label><input id="security-username" value={username} onChange={(e) => setUsername(e.target.value)} pattern="[A-Za-z0-9_]{3,50}" maxLength={50} required autoComplete="username" /><small className="field-help">3–50 letters, digits or underscores. Project ownership stays with your account.</small></div>
      <PasswordField id="security-new" label="New app password (optional)" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required={false} autoComplete="new-password" description="Use a unique passphrase of at least 15 characters. Leave blank to change only your username." />
      <PasswordField id="security-confirm" label="Confirm new password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required={Boolean(newPassword)} autoComplete="new-password" />
      {googleLinked && <label className="form-field"><span>Verify identity with</span><select value={method} onChange={(e) => { setMethod(e.target.value); setCredential(''); setCurrentPassword('') }}><option value="password">Current app password</option><option value="google">Linked Google account</option></select><small className="field-help">If you signed up with Google, verify with Google to create an app password. This does not change your Google password.</small></label>}
      {method === 'password' ? <PasswordField id="security-current" label="Current app password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} autoComplete="current-password" /> : <><GoogleSignInButton disabled={busy} onCredential={setCredential} onError={setError} />{credential && <p role="status">Google credential received. Save within five minutes.</p>}</>}
      {error && <p className="form-alert form-alert--error" role="alert">{error}</p>}
      <button className="text-button" type="button" onClick={() => { setUsername(profile.username); setCurrentPassword(''); setNewPassword(''); setConfirmPassword(''); setCredential(''); setError('') }}>Clear security changes</button>
      <button className="primary-button" disabled={busy || (username === profile.username && !newPassword) || (method === 'google' && !credential)}>{busy ? 'Verifying…' : 'Save security changes and sign out'}</button>
      </fieldset>
    </form>
  </section>
}
