import { useEffect, useState } from 'react'

import { getMyProfile, updateMyProfile } from '../api/userApi'
import AccountSecurityPanel from '../components/auth/AccountSecurityPanel'
import AppShell from '../components/layout/AppShell'
import StatePanel from '../components/ui/StatePanel'
import { formatDateTime, formatEnum } from '../utils/entityFormatters'
import getApiErrorMessage from '../utils/getApiErrorMessage'

function ProfilePage() {
  const [profile, setProfile] = useState(null)
  const [displayName, setDisplayName] = useState('')
  const [profilePictureUrl, setProfilePictureUrl] = useState('')
  const [imageFailed, setImageFailed] = useState(false)
  const [previewFailed, setPreviewFailed] = useState(false)
  const [error, setError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    async function loadProfile() {
      setError('')
      setSuccessMessage('')
      setIsLoading(true)
      try {
        const data = await getMyProfile(controller.signal)
        setProfile(data)
        setDisplayName(data.displayName || '')
        setProfilePictureUrl(data.profilePictureUrl || '')
        setImageFailed(false)
      } catch (apiError) {
        if (apiError.code !== 'ERR_CANCELED') setError(getApiErrorMessage(apiError, 'Unable to load your profile.'))
      } finally {
        if (!controller.signal.aborted) setIsLoading(false)
      }
    }

    loadProfile()
    return () => controller.abort()
  }, [reloadKey])

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setSuccessMessage('')

    const normalizedDisplayName = displayName.trim()
    const update = {}
    if (normalizedDisplayName !== (profile.displayName || '')) update.displayName = normalizedDisplayName
    if (profilePictureUrl.trim() !== (profile.profilePictureUrl || '')) update.profilePictureUrl = profilePictureUrl.trim()

    if (Object.keys(update).length === 0) {
      setSuccessMessage('No profile changes to save.')
      return
    }

    setIsSubmitting(true)
    try {
      const updatedProfile = await updateMyProfile(update)
      setProfile(updatedProfile)
      setDisplayName(updatedProfile.displayName || '')
      setProfilePictureUrl(updatedProfile.profilePictureUrl || '')
      setImageFailed(false)
      setSuccessMessage('Profile updated successfully.')
    } catch (apiError) {
      setError(getApiErrorMessage(apiError, 'Unable to update your profile.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AppShell>
        {isLoading && <StatePanel tone="loading" eyebrow="My profile" title="Loading your profile" description="Synchronizing account details and preferences." />}
        {!isLoading && error && !profile && <div className="detail-error"><p className="eyebrow">My profile</p><h1>Profile unavailable.</h1><p className="form-alert form-alert--error" role="alert">{error}</p><button className="text-button" type="button" onClick={() => setReloadKey((key) => key + 1)}>Try again</button></div>}

        {!isLoading && profile && (
          <>
            <div className="profile-hero">
              <div className="profile-avatar">
                {profile.profilePictureUrl && !imageFailed
                  ? <img src={profile.profilePictureUrl} alt={`${profile.displayName || profile.username} profile`} onError={() => setImageFailed(true)} />
                  : <span aria-hidden="true">{(profile.displayName || profile.username).charAt(0).toUpperCase()}</span>}
              </div>
              <div><p className="eyebrow">My profile</p><h1>{profile.displayName || profile.username}</h1><p className="dashboard-lead">Manage the personal fields supported by your account.</p></div>
              <span className={`status-badge ${profile.deactivated ? 'status-badge--deactivated' : 'status-badge--active'}`}>{profile.deactivated ? 'Deactivated' : 'Active account'}</span>
            </div>

            <div className="profile-layout">
              <section className="profile-panel">
                <div className="section-heading"><div><p className="eyebrow">Account information</p><h2>Identity and access</h2></div></div>
                <dl className="profile-facts">
                  <div><dt>Username</dt><dd>@{profile.username}</dd></div>
                  <div><dt>Email</dt><dd>{profile.email}</dd></div>
                  <div><dt>Role</dt><dd>{formatEnum(profile.role)}</dd></div>
                  <div><dt>Display name</dt><dd>{profile.displayName || 'Not set'}</dd></div>
                  <div><dt>Created</dt><dd>{formatDateTime(profile.createdAt)}</dd></div>
                  <div><dt>Last updated</dt><dd>{formatDateTime(profile.updatedAt)}</dd></div>
                </dl>
                <p className="privacy-note">Change your username in Account protection below. Email changes require address verification and are not available yet.</p>
              </section>

              <section className="profile-panel profile-panel--edit">
                <div className="section-heading"><div><p className="eyebrow">Editable details</p><h2>Update profile</h2></div></div>
                <form className="profile-form" onSubmit={handleSubmit}>
                  <div className="field-group"><label htmlFor="displayName">Display name</label><input id="displayName" name="displayName" value={displayName} onChange={(event) => setDisplayName(event.target.value)} minLength="2" maxLength="50" required /></div>
                  <div className="field-group">
                    <label htmlFor="profilePictureUrl">Profile picture URL</label>
                    <input id="profilePictureUrl" name="profilePictureUrl" type="url" value={profilePictureUrl} onChange={(event) => { setProfilePictureUrl(event.target.value); setPreviewFailed(false) }} placeholder="https://example.com/avatar.jpg" maxLength="255" pattern="https://.*" />
                    <div className="avatar-control"><span>Use a public HTTPS image URL.</span>{profilePictureUrl && <button className="text-button action-button--delete" type="button" onClick={() => setProfilePictureUrl('')}>Remove picture</button>}</div>
                  </div>
                  {profilePictureUrl.startsWith('https://') && (previewFailed ? <p role="status" className="field-help">Unable to preview this image. Check that the URL points to an accessible image.</p> : <div className="profile-avatar"><img key={profilePictureUrl} src={profilePictureUrl} alt="New avatar preview" referrerPolicy="no-referrer" onError={() => setPreviewFailed(true)} /></div>)}
                  <button className="text-button" type="button" disabled={isSubmitting} onClick={() => { setDisplayName(profile.displayName || ''); setProfilePictureUrl(profile.profilePictureUrl || ''); setPreviewFailed(false); setError(''); setSuccessMessage('') }}>Discard unsaved changes</button>
                  {error && <p className="form-alert form-alert--error" role="alert">{error}</p>}
                  {successMessage && <p className="form-alert form-alert--success" role="status">{successMessage}</p>}
                  <button className="primary-button" type="submit" disabled={isSubmitting}><span>{isSubmitting ? 'Saving...' : 'Save changes'}</span></button>
                </form>
              </section>
            </div>
            <AccountSecurityPanel profile={profile} />
          </>
        )}
    </AppShell>
  )
}

export default ProfilePage
