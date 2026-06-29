import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import Layout from './components/Layout'
import ProtectedRoute from './components/ProtectedRoute'
import ProductListPage from './pages/ProductListPage'
import ProductDetailPage from './pages/ProductDetailPage'
import CartPage from './pages/CartPage'
import CheckoutPage from './pages/CheckoutPage'
import MyOrdersPage from './pages/MyOrdersPage'
import MyPage from './pages/MyPage'
import OrderDetailPage from './pages/OrderDetailPage'
import GuestOrderLookupPage from './pages/GuestOrderLookupPage'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import SellerLayout from './pages/seller/SellerLayout'
import SellerProductsPage from './pages/seller/SellerProductsPage'
import SellerOrdersPage from './pages/seller/SellerOrdersPage'
import SellerSettlementsPage from './pages/seller/SellerSettlementsPage'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<ProductListPage />} />
            <Route path="products/:id" element={<ProductDetailPage />} />
            <Route path="login" element={<LoginPage />} />
            <Route path="signup" element={<SignupPage />} />
            {/* 장바구니·주문은 게스트(localStorage)와 회원 모두 사용 */}
            <Route path="cart" element={<CartPage />} />
            <Route path="checkout" element={<CheckoutPage />} />
            <Route path="orders/lookup" element={<GuestOrderLookupPage />} />
            <Route
              path="orders"
              element={
                <ProtectedRoute>
                  <MyOrdersPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="orders/:id"
              element={
                <ProtectedRoute>
                  <OrderDetailPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="my"
              element={
                <ProtectedRoute>
                  <MyPage />
                </ProtectedRoute>
              }
            />
            {/* 판매자 백오피스 (로그인 필요, 레이아웃이 ROLE_SELLER 게이트) */}
            <Route
              path="seller"
              element={
                <ProtectedRoute>
                  <SellerLayout />
                </ProtectedRoute>
              }
            >
              <Route index element={<SellerProductsPage />} />
              <Route path="products" element={<SellerProductsPage />} />
              <Route path="orders" element={<SellerOrdersPage />} />
              <Route path="settlements" element={<SellerSettlementsPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
