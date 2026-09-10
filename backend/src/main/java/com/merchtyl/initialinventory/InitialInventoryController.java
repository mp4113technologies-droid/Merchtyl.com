package com.merchtyl.initialinventory;

import com.merchtyl.common.PayloadTooLargeException;
import org.springframework.http.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;import java.util.UUID;import static com.merchtyl.initialinventory.InitialInventoryDtos.*;

@RestController @RequestMapping("/api/v1/stores/{storeId}/inventory/initial-setup")
public class InitialInventoryController {
 private static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";private final InitialInventoryService service;
 public InitialInventoryController(InitialInventoryService service){this.service=service;}
 @GetMapping("/template") @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_MANAGE) and @authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_CREATE)")
 public ResponseEntity<byte[]> template(@PathVariable UUID storeId,Authentication auth){return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX)).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=Merchtyl-Initial-Inventory.xlsx").body(service.template(storeId,auth));}
 @PostMapping(value="/validate",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_MANAGE) and @authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_CREATE)")
 public ValidationResponse validate(@PathVariable UUID storeId,@RequestPart("file") MultipartFile file,Authentication auth) throws IOException{if(file.getSize()>InitialInventoryWorkbookService.MAX_FILE_BYTES)throw new PayloadTooLargeException("INITIAL_INVENTORY_FILE_TOO_LARGE");return service.validate(storeId,file.getOriginalFilename(),file.getBytes(),auth);}
 @PostMapping("/{importId}/confirm") @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_MANAGE) and @authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_CREATE)")
 public ImportResult confirm(@PathVariable UUID storeId,@PathVariable UUID importId,Authentication auth){return service.confirm(storeId,importId,auth);}
}
