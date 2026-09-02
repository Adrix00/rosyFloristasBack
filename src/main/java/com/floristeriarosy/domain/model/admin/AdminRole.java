package com.floristeriarosy.domain.model.admin;

/**
 * The two admin roles (admin.md, section 1). {@code OWNER} can manage other administrators;
 * {@code ADMIN} cannot. In everything else — catalog, orders, stock, purchasing — they have
 * identical permissions.
 */
public enum AdminRole {
  OWNER,
  ADMIN
}
