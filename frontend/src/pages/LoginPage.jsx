import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { linkGoogleAccount, login, loginWithGoogle } from '../api/authApi'
import GoogleSignInButton from '../components/auth/GoogleSignInButton'
import PasswordField from '../components/auth/PasswordField'
import AppLogo from '../components/icons/AppLogo'
import Alert from '../components/ui/Alert'
import ConfirmationPanel from '../components/ui/ConfirmationPanel'

function LoginPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [securityUpdated] = useState(() => sessionStorage.getItem('securityUpdated') === 'true')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isGoogleSubmitting, setIsGoogleSubmitting] = useState(false)
  const [pendingGoogleCredential, setPendingGoogleCredential] = useState('')
  const [linkPassword, setLinkPassword] = useState('')

  function completeLogin(data) {
    sessionStorage.removeItem('securityUpdated')
    if (!data?.accessToken) throw new Error('Access token was not returned by the server')
    localStorage.setItem('accessToken', data.accessToken)
    navigate('/dashboard')
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setIsSubmitting(true)

    try {
      const data = await login({ username, password })
      completeLogin(data)
    } catch (apiError) {
      setError(apiError.response?.data?.message || apiError.message || 'Unable to sign in. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleGoogleCredential(idToken) {
    if (isSubmitting || isGoogleSubmitting) return
    setError('')
    setPendingGoogleCredential('')
    setLinkPassword('')
    setIsGoogleSubmitting(true)

    try {
      const data = await loginWithGoogle(idToken)
      completeLogin(data)
    } catch (apiError) {
      if (apiError.response?.status === 409) {
        setPendingGoogleCredential(idToken)
        setError('This email already has an account. Confirm its password to link Google safely.')
      } else {
        setError(apiError.response?.data?.message || apiError.message || 'Unable to sign in with Google. Please try again.')
      }
    } finally {
      setIsGoogleSubmitting(false)
    }
  }

  async function handleGoogleLink(event) {
    event.preventDefault()
    if (!pendingGoogleCredential || isGoogleSubmitting) return

    setError('')
    setIsGoogleSubmitting(true)
    try {
      const data = await linkGoogleAccount(pendingGoogleCredential, linkPassword)
      completeLogin(data)
    } catch (apiError) {
      setError(apiError.response?.data?.message || apiError.message || 'Unable to link this Google account.')
    } finally {
      setIsGoogleSubmitting(false)
    }
  }

  function cancelGoogleLink() {
    setPendingGoogleCredential('')
    setLinkPassword('')
    setError('')
  }

  return (
    <main className="auth-shell">
      <section className="auth-story" aria-labelledby="product-title">
        <AppLogo className="brand-mark" />
        <div className="story-copy">
          <p className="eyebrow">A calmer way to make progress</p>
          <h1 id="product-title">Plan clearly.<br />Spend mindfully.</h1>
          <p className="story-description">Keep tasks and everyday expenses together in one focused workspace, built to help you finish what matters.</p>
        </div>
        <div className="focus-card" aria-label="Today overview preview">
          <div className="focus-card__header"><span>Today&apos;s focus</span><span>3 tasks</span></div>
          <div className="focus-progress" aria-hidden="true"><span /></div>
          <p>Two priorities completed. One thoughtful step left.</p>
        </div>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <div className="auth-heading">
            <p className="eyebrow">Welcome back</p>
            <h2>Sign in to your workspace</h2>
            <p>Continue managing your tasks and personal expenses.</p>
          </div>
          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="field-group">
              <label htmlFor="username">Username</label>
              <input id="username" name="username" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="Enter your username" autoComplete="username" required />
            </div>
            <PasswordField
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
            />
            <button className="primary-button" type="submit" disabled={isSubmitting || isGoogleSubmitting}>
              <span>{isSubmitting ? 'Signing in…' : 'Sign in'}</span>
            </button>
          </form>
          <div className="auth-divider" aria-hidden="true"><span>or</span></div>
          {securityUpdated && <p className="form-alert form-alert--success" role="status">Account security updated. Sign in again with your updated details.</p>}
          <GoogleSignInButton
            disabled={isSubmitting || isGoogleSubmitting}
            onCredential={handleGoogleCredential}
            onError={setError}
          />
          {error && <Alert tone="error">{error}</Alert>}
          {pendingGoogleCredential && (
            <ConfirmationPanel
              title="Confirm your existing account"
              description="Your data stays in place. We will link Google only after the current password is verified."
            >
              <form className="google-link-form" onSubmit={handleGoogleLink}>
                <PasswordField
                  id="link-password"
                  label="Current account password"
                  value={linkPassword}
                  onChange={(event) => setLinkPassword(event.target.value)}
                  autoComplete="current-password"
                />
                <div className="google-link-panel__actions">
                  <button className="text-button" type="button" onClick={cancelGoogleLink}>Cancel</button>
                  <button className="primary-button primary-button--fit" type="submit" disabled={isGoogleSubmitting}>
                    {isGoogleSubmitting ? 'Linking…' : 'Confirm and link'}
                  </button>
                </div>
              </form>
            </ConfirmationPanel>
          )}
          <p className="auth-note">
            New here?{' '}
            <button className="text-button" type="button" onClick={() => navigate('/register')}>
              Create an account
            </button>
          </p>
        </div>
        <p className="auth-footer">Task Management · Built for steady progress</p>
      </section>
    </main>
  )
}

export default LoginPage
