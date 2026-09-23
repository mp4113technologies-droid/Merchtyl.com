package com.merchtyl.inventoryimport;
import com.merchtyl.common.PayloadTooLargeException;
import com.merchtyl.inventoryimport.InventoryImportDtos.*;
import org.springframework.http.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;import java.util.UUID;
@RestController @RequestMapping("/api/v1/stores/{storeId}/inventory/import")
@PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_MANAGE, T(com.merchtyl.security.PermissionCode).INVENTORY_RECEIVE, T(com.merchtyl.security.PermissionCode).INVENTORY_ADJUST)")
public class InventoryUpdateImportController {
 private static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";private final InventoryUpdateImportService service;public InventoryUpdateImportController(InventoryUpdateImportService service){this.service=service;}
 @GetMapping("/workbook") public ResponseEntity<byte[]> download(@PathVariable UUID storeId,Authentication auth){return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX)).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=Merchtyl-Store-Inventory.xlsx").body(service.download(storeId,auth));}
 @PostMapping(value="/validate",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ValidationResponse validate(@PathVariable UUID storeId,@RequestPart("file") MultipartFile file,Authentication auth)throws IOException{if(file.getSize()>InventoryUpdateWorkbookService.MAX_FILE_BYTES)throw new PayloadTooLargeException("INVENTORY_IMPORT_FILE_TOO_LARGE");return service.validate(storeId,file.getOriginalFilename(),file.getBytes(),auth);}
 @GetMapping("/{importId}/preview") public ValidationResponse preview(@PathVariable UUID storeId,@PathVariable UUID importId,Authentication auth){return service.preview(storeId,importId,auth);}
 @PostMapping("/{importId}/confirm") public ConfirmResponse confirm(@PathVariable UUID storeId,@PathVariable UUID importId,Authentication auth){return service.confirm(storeId,importId,auth);}
}
