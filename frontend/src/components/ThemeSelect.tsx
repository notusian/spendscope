import { useId } from 'react'
import { useTheme, type ThemePreference } from '../hooks/useTheme'

export default function ThemeSelect() {
  const id = useId()
  const { preference, setPreference } = useTheme()
  return (
    <div className="theme-select">
      <label htmlFor={id}>Appearance</label>
      <select
        id={id}
        className="select"
        value={preference}
        onChange={(event) => setPreference(event.target.value as ThemePreference)}
      >
        <option value="system">System</option>
        <option value="light">Light</option>
        <option value="dark">Dark</option>
      </select>
    </div>
  )
}
