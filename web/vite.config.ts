import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// base './' para que funcione en GitHub Pages (https://usuario.github.io/repo/).
export default defineConfig({ plugins: [react()], base: './' })
