import { createRoot } from 'react-dom/client'
import App from './App'
import './ui/theme.css'

createRoot(document.getElementById('root')!).render(<App />)

// Funciona sin conexión y se puede "instalar" en la pantalla de inicio.
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => { navigator.serviceWorker.register('./sw.js').catch(() => {}) })
}
