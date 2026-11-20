import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { Bitacora } from './modulos/bitacora/Bitacora'
import { BuscarProducto } from './modulos/catalogo/BuscarProducto'
import { EditarProducto } from './modulos/catalogo/EditarProducto'
import { ListaProductos } from './modulos/catalogo/ListaProductos'
import { NuevoProducto } from './modulos/catalogo/NuevoProducto'
import { PaginaConexionFel } from './modulos/configuracion/ConexionFel'
import { PaginaEmisor } from './modulos/configuracion/DatosEmisor'
import { PaginaEstablecimiento } from './modulos/configuracion/DatosEstablecimiento'
import { PaginaCaja } from './modulos/configuracion/FormularioCaja'
import { PaginaImpresoras } from './modulos/configuracion/Impresoras'
import { PaginaAnularFacturas } from './modulos/facturas/AnularFacturas'
import { PaginaFacturasRechazadas } from './modulos/facturas/FacturasRechazadas'
import { ListaCajas } from './modulos/configuracion/ListaCajas'
import { Inicio } from './modulos/inicio/Inicio'
import { CambioCredencial } from './modulos/login/CambioCredencial'
import { Login } from './modulos/login/Login'
import { RutaProtegida } from './modulos/login/RutaProtegida'
import { EstadoSistema } from './modulos/sistema/EstadoSistema'
import { EditarUsuario } from './modulos/usuarios/EditarUsuario'
import { ListaUsuarios } from './modulos/usuarios/ListaUsuarios'
import { NuevoUsuario } from './modulos/usuarios/NuevoUsuario'
import { RestablecerCredencial } from './modulos/usuarios/RestablecerCredencial'
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
            path="/usuarios"
            element={
              <RutaProtegida>
                <ListaUsuarios />
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
          <Route
            path="/usuarios/:id/editar"
            element={
              <RutaProtegida>
                <EditarUsuario />
              </RutaProtegida>
            }
          />
          <Route
            path="/usuarios/:id/credencial"
            element={
              <RutaProtegida>
                <RestablecerCredencial />
              </RutaProtegida>
            }
          />
          <Route
            path="/configuracion/emisor"
            element={
              <RutaProtegida>
                <PaginaEmisor />
              </RutaProtegida>
            }
          />
          <Route
            path="/configuracion/establecimiento"
            element={
              <RutaProtegida>
                <PaginaEstablecimiento />
              </RutaProtegida>
            }
          />
          <Route
            path="/configuracion/fel"
            element={
              <RutaProtegida>
                <PaginaConexionFel />
              </RutaProtegida>
            }
          />
          <Route
            path="/facturas/anular"
            element={
              <RutaProtegida>
                <PaginaAnularFacturas />
              </RutaProtegida>
            }
          />
          <Route
            path="/facturas/rechazadas"
            element={
              <RutaProtegida>
                <PaginaFacturasRechazadas />
              </RutaProtegida>
            }
          />
          <Route
            path="/configuracion/impresoras"
            element={
              <RutaProtegida>
                <PaginaImpresoras />
              </RutaProtegida>
            }
          />
          <Route
            path="/configuracion/cajas"
            element={
              <RutaProtegida>
                <ListaCajas />
              </RutaProtegida>
            }
          />
          <Route
            path="/configuracion/cajas/nueva"
            element={
              <RutaProtegida>
                <PaginaCaja />
              </RutaProtegida>
            }
          />
          <Route
            path="/configuracion/cajas/:id/editar"
            element={
              <RutaProtegida>
                <PaginaCaja />
              </RutaProtegida>
            }
          />
          <Route
            path="/bitacora"
            element={
              <RutaProtegida>
                <Bitacora />
              </RutaProtegida>
            }
          />
          <Route
            path="/catalogo"
            element={
              <RutaProtegida>
                <ListaProductos />
              </RutaProtegida>
            }
          />
          <Route
            path="/catalogo/buscar"
            element={
              <RutaProtegida>
                <BuscarProducto />
              </RutaProtegida>
            }
          />
          <Route
            path="/catalogo/nuevo"
            element={
              <RutaProtegida>
                <NuevoProducto />
              </RutaProtegida>
            }
          />
          <Route
            path="/catalogo/:id/editar"
            element={
              <RutaProtegida>
                <EditarProducto />
              </RutaProtegida>
            }
          />
          <Route
            path="/credencial"
            element={
              <RutaProtegida>
                <CambioCredencial />
              </RutaProtegida>
            }
          />
          <Route path="*" element={<Navigate to="/inicio" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
