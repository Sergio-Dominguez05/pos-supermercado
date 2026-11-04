import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { Inicio } from './modulos/inicio/Inicio'
import { Login } from './modulos/login/Login'
import { RutaProtegida } from './modulos/login/RutaProtegida'
import { EstadoSistema } from './modulos/sistema/EstadoSistema'
import { NuevoUsuario } from './modulos/usuarios/NuevoUsuario'
import { Venta } from './modulos/venta/Venta'

const clienteConsultas = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

// Cada sprint agrega aquí las rutas de su módulo (login, venta, caja, catálogo, ...).
export default function App() {
  return (
    <QueryClientProvider client={clienteConsultas}>
      <BrowserRouter>
        <Routes>
          <Route path="/sistema" element={<EstadoSistema />} />
          <Route path="/login" element={<Login />} />
          <Route
            path="/inicio"
            element={
              <RutaProtegida>
                <Inicio />
              </RutaProtegida>
            }
          />
          <Route
            path="/venta"
            element={
              <RutaProtegida>
                <Venta />
              </RutaProtegida>
            }
          />
          <Route
            path="/usuarios/nuevo"
            element={
              <RutaProtegida>
                <NuevoUsuario />
              </RutaProtegida>
            }
          />
          <Route path="*" element={<Navigate to="/inicio" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
