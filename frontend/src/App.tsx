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
import MyLoyaltyTierPage from './pages/MyLoyaltyTierPage'
import NotificationsPage from './pages/NotificationsPage'
import OrderDetailPage from './pages/OrderDetailPage'
import GiftClaimPage from './pages/GiftClaimPage'
import GuestOrderLookupPage from './pages/GuestOrderLookupPage'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import SellerLayout from './pages/seller/SellerLayout'
import SellerProductsPage from './pages/seller/SellerProductsPage'
import SellerOrdersPage from './pages/seller/SellerOrdersPage'
import SellerSettlementsPage from './pages/seller/SellerSettlementsPage'
import SellerFlashSalesPage from './pages/seller/SellerFlashSalesPage'
import AdminLayout from './pages/admin/AdminLayout'
import AdminSellersPage from './pages/admin/AdminSellersPage'
import AdminOrdersPage from './pages/admin/AdminOrdersPage'
import AdminCouponsPage from './pages/admin/AdminCouponsPage'
import AdminSettlementsPage from './pages/admin/AdminSettlementsPage'
import AdminReviewsPage from './pages/admin/AdminReviewsPage'
import AdminCollectionsPage from './pages/admin/AdminCollectionsPage'
import AdminCollectionEditPage from './pages/admin/AdminCollectionEditPage'
import AdminFlashSalesPage from './pages/admin/AdminFlashSalesPage'
import AdminDeliverySlotsPage from './pages/admin/AdminDeliverySlotsPage'
import AdminDeliveryRegionsPage from './pages/admin/AdminDeliveryRegionsPage'
import AdminMembershipsPage from './pages/admin/AdminMembershipsPage'
import AdminMembershipDetailPage from './pages/admin/AdminMembershipDetailPage'
import AdminDeliverySubscriptionsPage from './pages/admin/AdminDeliverySubscriptionsPage'
import AdminDeliverySubscriptionDetailPage from './pages/admin/AdminDeliverySubscriptionDetailPage'
import AdminGiftClaimsPage from './pages/admin/AdminGiftClaimsPage'
import AdminLoyaltyTiersPage from './pages/admin/AdminLoyaltyTiersPage'

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
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<ProductListPage />} />
            <Route path="products/:id" element={<ProductDetailPage />} />
            <Route path="collections" element={<CollectionsPage />} />
            <Route path="collections/:id" element={<CollectionDetailPage />} />
            <Route path="flash-sales" element={<FlashSalesPage />} />
            <Route path="login" element={<LoginPage />} />
            <Route path="signup" element={<SignupPage />} />
            {/* 장바구니·주문은 게스트(localStorage)와 회원 모두 사용 */}
            <Route path="cart" element={<CartPage />} />
            <Route path="checkout" element={<CheckoutPage />} />
            <Route path="orders/lookup" element={<GuestOrderLookupPage />} />
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
              <Route index element={<SellerProductsPage />} />
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
              <Route index element={<AdminSellersPage />} />
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
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
        </BrowserRouter>
        <Toaster position="top-center" richColors />
        </WishlistProvider>
      </AuthProvider>
    </ThemeProvider>
  )
}
