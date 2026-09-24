import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from 'next-themes'
import { AuthProvider } from './auth/AuthContext'
import { WishlistProvider } from './hooks/useWishlist'
import { Toaster } from '@/components/ui/sonner'
import Layout from './components/Layout'
import ProtectedRoute from './components/ProtectedRoute'
import ProductListPage from './pages/ProductListPage'
import ProductDetailPage from './pages/ProductDetailPage'
import CollectionsPage from './pages/CollectionsPage'
import CollectionDetailPage from './pages/CollectionDetailPage'
import FlashSalesPage from './pages/FlashSalesPage'
import CartPage from './pages/CartPage'
import CheckoutPage from './pages/CheckoutPage'
import MyOrdersPage from './pages/MyOrdersPage'
import MyPage from './pages/MyPage'
import MyReviewsPage from './pages/MyReviewsPage'
import MyRestockAlertsPage from './pages/MyRestockAlertsPage'
import MyMembershipPage from './pages/MyMembershipPage'
import MyDeliverySubscriptionsPage from './pages/MyDeliverySubscriptionsPage'
import MyWishlistPage from './pages/MyWishlistPage'
import MyAddressesPage from './pages/MyAddressesPage'
import MyLoyaltyTierPage from './pages/MyLoyaltyTierPage'
import NotificationsPage from './pages/NotificationsPage'
import OrderDetailPage from './pages/OrderDetailPage'
import GiftClaimPage from './pages/GiftClaimPage'
import GuestOrderLookupPage from './pages/GuestOrderLookupPage'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import PasswordResetPage from './pages/PasswordResetPage'
import TossPaymentResultPage from './pages/TossPaymentResultPage'
// 판매자·관리자 백오피스는 일부 사용자만 쓰므로 별도 청크로 분리해 고객 첫 로딩에서 제외한다.
const SellerLayout = lazy(() => import('./pages/seller/SellerLayout'))
const SellerDashboardPage = lazy(() => import('./pages/seller/SellerDashboardPage'))
const SellerProductsPage = lazy(() => import('./pages/seller/SellerProductsPage'))
const SellerOrdersPage = lazy(() => import('./pages/seller/SellerOrdersPage'))
const SellerSettlementsPage = lazy(() => import('./pages/seller/SellerSettlementsPage'))
const SellerFlashSalesPage = lazy(() => import('./pages/seller/SellerFlashSalesPage'))
const AdminLayout = lazy(() => import('./pages/admin/AdminLayout'))
const AdminDashboardPage = lazy(() => import('./pages/admin/AdminDashboardPage'))
const AdminSellersPage = lazy(() => import('./pages/admin/AdminSellersPage'))
const AdminOrdersPage = lazy(() => import('./pages/admin/AdminOrdersPage'))
const AdminCouponsPage = lazy(() => import('./pages/admin/AdminCouponsPage'))
const AdminSettlementsPage = lazy(() => import('./pages/admin/AdminSettlementsPage'))
const AdminReviewsPage = lazy(() => import('./pages/admin/AdminReviewsPage'))
const AdminCollectionsPage = lazy(() => import('./pages/admin/AdminCollectionsPage'))
const AdminCollectionEditPage = lazy(() => import('./pages/admin/AdminCollectionEditPage'))
const AdminFlashSalesPage = lazy(() => import('./pages/admin/AdminFlashSalesPage'))
const AdminDeliverySlotsPage = lazy(() => import('./pages/admin/AdminDeliverySlotsPage'))
const AdminDeliveryRegionsPage = lazy(() => import('./pages/admin/AdminDeliveryRegionsPage'))
const AdminMembershipsPage = lazy(() => import('./pages/admin/AdminMembershipsPage'))
const AdminMembershipDetailPage = lazy(() => import('./pages/admin/AdminMembershipDetailPage'))
const AdminDeliverySubscriptionsPage = lazy(() => import('./pages/admin/AdminDeliverySubscriptionsPage'))
const AdminDeliverySubscriptionDetailPage = lazy(
  () => import('./pages/admin/AdminDeliverySubscriptionDetailPage'),
)
const AdminGiftClaimsPage = lazy(() => import('./pages/admin/AdminGiftClaimsPage'))
const AdminLoyaltyTiersPage = lazy(() => import('./pages/admin/AdminLoyaltyTiersPage'))
const AdminUsersPage = lazy(() => import('./pages/admin/AdminUsersPage'))
const AdminPointsPage = lazy(() => import('./pages/admin/AdminPointsPage'))
const AdminAuditLogsPage = lazy(() => import('./pages/admin/AdminAuditLogsPage'))

export default function App() {
  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
    >
      <AuthProvider>
        <WishlistProvider>
        <BrowserRouter>
        <Suspense
          fallback={
            <p role="status" className="py-10 text-center text-sm text-muted-foreground">
              불러오는 중…
            </p>
          }
        >
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<ProductListPage />} />
            <Route path="products/:id" element={<ProductDetailPage />} />
            <Route path="collections" element={<CollectionsPage />} />
            <Route path="collections/:id" element={<CollectionDetailPage />} />
            <Route path="flash-sales" element={<FlashSalesPage />} />
            <Route path="login" element={<LoginPage />} />
            <Route path="signup" element={<SignupPage />} />
            <Route path="reset-password" element={<PasswordResetPage />} />
            {/* 장바구니·주문은 게스트(localStorage)와 회원 모두 사용 */}
            <Route path="cart" element={<CartPage />} />
            <Route path="checkout" element={<CheckoutPage />} />
            <Route path="orders/lookup" element={<GuestOrderLookupPage />} />
            {/* 토스 결제창 복귀(회원·비회원 공용, VITE_TOSS_CLIENT_KEY 설정 시) */}
            <Route path="payments/toss/:result" element={<TossPaymentResultPage />} />
            {/* 선물 수령(비회원 접근 가능, `docs/planning/gift-order.md` AC6) */}
            <Route path="gift/:token" element={<GiftClaimPage />} />
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
            <Route
              path="my/reviews"
              element={
                <ProtectedRoute>
                  <MyReviewsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="my/restock-alerts"
              element={
                <ProtectedRoute>
                  <MyRestockAlertsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="my/membership"
              element={
                <ProtectedRoute>
                  <MyMembershipPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="my/delivery-subscriptions"
              element={
                <ProtectedRoute>
                  <MyDeliverySubscriptionsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="my/addresses"
              element={
                <ProtectedRoute>
                  <MyAddressesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="my/wishlist"
              element={
                <ProtectedRoute>
                  <MyWishlistPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="my/loyalty-tier"
              element={
                <ProtectedRoute>
                  <MyLoyaltyTierPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="notifications"
              element={
                <ProtectedRoute>
                  <NotificationsPage />
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
              <Route index element={<SellerDashboardPage />} />
              <Route path="dashboard" element={<SellerDashboardPage />} />
              <Route path="products" element={<SellerProductsPage />} />
              <Route path="orders" element={<SellerOrdersPage />} />
              <Route path="settlements" element={<SellerSettlementsPage />} />
              <Route path="flash-sales" element={<SellerFlashSalesPage />} />
            </Route>
            {/* 관리자 백오피스 (로그인 필요, 레이아웃이 ROLE_ADMIN 게이트) */}
            <Route
              path="admin"
              element={
                <ProtectedRoute>
                  <AdminLayout />
                </ProtectedRoute>
              }
            >
              <Route index element={<AdminDashboardPage />} />
              <Route path="sellers" element={<AdminSellersPage />} />
              <Route path="orders" element={<AdminOrdersPage />} />
              <Route path="coupons" element={<AdminCouponsPage />} />
              <Route path="settlements" element={<AdminSettlementsPage />} />
              <Route path="reviews" element={<AdminReviewsPage />} />
              <Route path="collections" element={<AdminCollectionsPage />} />
              <Route path="collections/new" element={<AdminCollectionEditPage />} />
              <Route path="collections/:id" element={<AdminCollectionEditPage />} />
              <Route path="flash-sales" element={<AdminFlashSalesPage />} />
              <Route path="delivery-slots" element={<AdminDeliverySlotsPage />} />
              <Route path="delivery-regions" element={<AdminDeliveryRegionsPage />} />
              <Route path="memberships" element={<AdminMembershipsPage />} />
              <Route path="memberships/:id" element={<AdminMembershipDetailPage />} />
              <Route path="delivery-subscriptions" element={<AdminDeliverySubscriptionsPage />} />
              <Route path="delivery-subscriptions/:id" element={<AdminDeliverySubscriptionDetailPage />} />
              <Route path="gift-claims" element={<AdminGiftClaimsPage />} />
              <Route path="loyalty-tiers" element={<AdminLoyaltyTiersPage />} />
              <Route path="users" element={<AdminUsersPage />} />
              <Route path="points" element={<AdminPointsPage />} />
              <Route path="audit-logs" element={<AdminAuditLogsPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
        </Suspense>
        </BrowserRouter>
        <Toaster position="top-center" richColors />
        </WishlistProvider>
      </AuthProvider>
    </ThemeProvider>
  )
}
