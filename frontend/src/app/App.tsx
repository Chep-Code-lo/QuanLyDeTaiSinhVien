import { AuthProvider } from '../context/AuthContext'
import { ToastProvider } from '../context/ToastContext'
import { AppRoutes } from '../routes/AppRoutes'
import '../styles/app.css'
import '../styles/student-workspace.css'
import '../styles/ui.css'

function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </ToastProvider>
  )
}

export default App
