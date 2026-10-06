package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.CartItemRequest;
import com.gmc.backend.dto.response.CartResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Cart;
import com.gmc.backend.model.CartItem;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.ProductCategory;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.CartItemRepository;
import com.gmc.backend.repository.CartRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductServiceImpl productServiceImpl;

    @InjectMocks
    private CartServiceImpl cartService;

    private User user() {
        Role role = Role.builder().roleId(1L).roleName("CUSTOMER").build();
        return User.builder().userId(1L).name("John").email("john@example.com")
                .password("pass").role(role).build();
    }

    private Cart cartFor(User u) {
        Cart c = new Cart();
        c.setCartId(1L);
        c.setUser(u);
        c.setCartItems(new ArrayList<>());
        return c;
    }

    private Product product(int stock) {
        ProductCategory cat = ProductCategory.builder().categoryId(1L).name("Cat").build();
        return Product.builder().productId(1L).name("Item")
                .price(new BigDecimal("50.00")).category(cat).stockQuantity(stock).build();
    }

    @Test
    void getCart_shouldCreateCartWhenNoneExists() {
        User u = user();
        Cart newCart = cartFor(u);
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(cartRepository.save(any())).thenReturn(newCart);
        when(cartItemRepository.findByCart(newCart)).thenReturn(List.of());

        CartResponse result = cartService.getCart(1L);

        assertNotNull(result);
        assertEquals(1L, result.getCartId());
    }

    @Test
    void getCart_shouldReturnExistingCart() {
        User u = user();
        Cart existing = cartFor(u);
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.findByCart(existing)).thenReturn(List.of());

        CartResponse result = cartService.getCart(1L);

        assertEquals(1L, result.getCartId());
    }

    @Test
    void getCart_shouldThrowWhenUserNotFoundAndNoCart() {
        when(cartRepository.findByUser_UserId(99L)).thenReturn(Optional.empty());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.getCart(99L));
    }

    @Test
    void addItem_shouldThrowWhenStockInsufficient() {
        User u = user();
        Cart c = cartFor(u);
        Product p = product(2);
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(c));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        CartItemRequest req = new CartItemRequest();
        req.setProductId(1L);
        req.setQuantity(10);

        assertThrows(BusinessRuleException.class, () -> cartService.addItem(1L, req));
    }

    @Test
    void addItem_shouldAddNewItemWhenNotAlreadyInCart() {
        User u = user();
        Cart c = cartFor(u);
        Product p = product(20);
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(c));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(cartItemRepository.findByCartAndProduct(c, p)).thenReturn(Optional.empty());
        when(cartRepository.findById(1L)).thenReturn(Optional.of(c));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of());

        CartItemRequest req = new CartItemRequest();
        req.setProductId(1L);
        req.setQuantity(5);

        CartResponse result = cartService.addItem(1L, req);

        assertNotNull(result);
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void addItem_shouldUpdateQuantityWhenItemAlreadyInCart() {
        User u = user();
        Cart c = cartFor(u);
        Product p = product(20);
        CartItem existing = new CartItem();
        existing.setCartItemId(1L);
        existing.setCart(c);
        existing.setProduct(p);
        existing.setQuantity(3);
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(c));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(cartItemRepository.findByCartAndProduct(c, p)).thenReturn(Optional.of(existing));
        when(cartRepository.findById(1L)).thenReturn(Optional.of(c));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of());

        CartItemRequest req = new CartItemRequest();
        req.setProductId(1L);
        req.setQuantity(2);

        cartService.addItem(1L, req);

        assertEquals(5, existing.getQuantity());
        verify(cartItemRepository).save(existing);
    }

    @Test
    void updateItemQuantity_shouldDeleteItemWhenQuantityIsZeroOrLess() {
        User u = user();
        Cart c = cartFor(u);
        CartItem item = new CartItem();
        item.setCartItemId(10L);
        item.setCart(c);
        item.setQuantity(3);
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(c));
        when(cartItemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(cartRepository.findById(1L)).thenReturn(Optional.of(c));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of());

        cartService.updateItemQuantity(1L, 10L, 0);

        verify(cartItemRepository).delete(item);
    }

    @Test
    void updateItemQuantity_shouldThrowWhenItemBelongsToDifferentCart() {
        User u = user();
        Cart myCart = cartFor(u);
        Cart otherCart = new Cart();
        otherCart.setCartId(99L);
        CartItem item = new CartItem();
        item.setCartItemId(5L);
        item.setCart(otherCart);
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(myCart));
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        assertThrows(BusinessRuleException.class, () -> cartService.updateItemQuantity(1L, 5L, 2));
    }

    @Test
    void removeItem_shouldThrowWhenItemBelongsToDifferentUser() {
        User u = user();
        Cart myCart = cartFor(u);
        Cart otherCart = new Cart();
        otherCart.setCartId(99L);
        CartItem item = new CartItem();
        item.setCartItemId(5L);
        item.setCart(otherCart);
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(myCart));
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        assertThrows(BusinessRuleException.class, () -> cartService.removeItem(1L, 5L));
    }

    @Test
    void clearCart_shouldCallDeleteByCart() {
        User u = user();
        Cart c = cartFor(u);
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(c));

        cartService.clearCart(1L);

        verify(cartItemRepository).deleteByCart(c);
    }
}
