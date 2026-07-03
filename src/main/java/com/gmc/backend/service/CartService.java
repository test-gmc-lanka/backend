package com.gmc.backend.service;

import com.gmc.backend.dto.request.CartItemRequest;
import com.gmc.backend.dto.response.CartResponse;

public interface CartService {

    CartResponse getCart(Long userId);

    CartResponse addItem(Long userId, CartItemRequest request);

    CartResponse updateItemQuantity(Long userId, Long cartItemId, Integer quantity);

    CartResponse removeItem(Long userId, Long cartItemId);

    void clearCart(Long userId);
}
