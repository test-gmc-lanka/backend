package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.CartItemRequest;
import com.gmc.backend.dto.response.CartItemResponse;
import com.gmc.backend.dto.response.CartResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Cart;
import com.gmc.backend.model.CartItem;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.CartItemRepository;
import com.gmc.backend.repository.CartRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.CartService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final int MAX_QUANTITY_PER_ITEM = 999;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductServiceImpl productServiceImpl;

    @Override
    public CartResponse getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(Long userId, CartItemRequest request) {
        Cart cart = getOrCreateCart(userId);
        Product product = productRepository
                .findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        if (Boolean.FALSE.equals(product.getIsActive())) {
            throw new BusinessRuleException(product.getName() + " is no longer available");
        }
        // Check what the cart would hold in total, not just this addition
        int inCart = cartItemRepository
                .findByCartAndProduct(cart, product)
                .map(CartItem::getQuantity)
                .orElse(0);
        requireStock(product, inCart + request.getQuantity());
        cartItemRepository
                .findByCartAndProduct(cart, product)
                .ifPresentOrElse(
                        existing -> {
                            existing.setQuantity(existing.getQuantity() + request.getQuantity());
                            cartItemRepository.save(existing);
                        },
                        () -> {
                            CartItem item = new CartItem();
                            item.setCart(cart);
                            item.setProduct(product);
                            item.setQuantity(request.getQuantity());
                            cartItemRepository.save(item);
                        });
        return toResponse(cartRepository.findById(cart.getCartId()).orElse(cart));
    }

    @Override
    @Transactional
    public CartResponse updateItemQuantity(Long userId, Long cartItemId, Integer quantity) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository
                .findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + cartItemId));
        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new BusinessRuleException("Cart item does not belong to this user");
        }
        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            requireStock(item.getProduct(), quantity);
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }
        return toResponse(cartRepository.findById(cart.getCartId()).orElse(cart));
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long userId, Long cartItemId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository
                .findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + cartItemId));
        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new BusinessRuleException("Cart item does not belong to this user");
        }
        cartItemRepository.delete(item);
        return toResponse(cartRepository.findById(cart.getCartId()).orElse(cart));
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cartItemRepository.deleteByCart(cart);
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUser_UserId(userId).orElseGet(() -> {
            User user = userRepository
                    .findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            Cart newCart = new Cart();
            newCart.setUser(user);
            newCart.setCartItems(new ArrayList<>());
            return cartRepository.save(newCart);
        });
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCart(cart);
        List<CartItemResponse> itemResponses = items.stream()
                .map(item -> CartItemResponse.builder()
                        .cartItemId(item.getCartItemId())
                        .product(productServiceImpl.toResponse(item.getProduct()))
                        .quantity(item.getQuantity())
                        .subtotal(item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .collect(Collectors.toList());
        BigDecimal total =
                itemResponses.stream().map(CartItemResponse::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return CartResponse.builder()
                .cartId(cart.getCartId())
                .items(itemResponses)
                .totalAmount(total)
                .build();
    }

    private static void requireStock(Product product, int wanted) {
        int stock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        if (wanted > MAX_QUANTITY_PER_ITEM) {
            throw new BusinessRuleException("You can order at most " + MAX_QUANTITY_PER_ITEM + " of one item");
        }
        if (wanted > stock) {
            throw new BusinessRuleException(
                    stock == 0
                            ? product.getName() + " is out of stock"
                            : "Only " + stock + " of " + product.getName() + " in stock");
        }
    }
}
