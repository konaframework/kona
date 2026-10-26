# Firefly screenshots

Each scene is a pair of PNGs in this folder. `<name>-light.png` is the light theme. `<name>-dark.png` is the dark theme. A page names the scene as `<name>.png` (for example `desk-panel.png`). That unsuffixed name is not an image the site loads. Drop both files of the pair in this folder and rebuild the docs site. The page shows the light file when the docs theme is light and the dark file when the docs theme is dark. The description is the alt text and the caption, and it describes the scene. If either file is missing, the page shows a placeholder that lists both filenames, this folder (`apps/docs/public/images/firefly/`), and the description. It does not link a missing file.

Files are served as `/images/firefly/<file>`.

| Light | Dark | Description |
|-------|------|-------------|
| `desk-panel-light.png` | `desk-panel-dark.png` | The hello-kotlin panel at http://127.0.0.1:4017/admin/desk, with the sidebar and the Desk page in the body. |
| `app-ready-light.png` | `app-ready-dark.png` | The hello-kotlin welcome page at http://127.0.0.1:4017/, a Kona app serving a route before you open the panel. |
| `empty-admin-light.png` | `empty-admin-dark.png` | The /admin path of a new app from kona new --kit=firefly, before any page or resource is added. |
| `panel-directory-light.png` | `panel-directory-dark.png` | The hello-kotlin app/firefly directory, showing Panel.kt, pages/, widgets/, and resources/. |
| `notes-manage-light.png` | `notes-manage-dark.png` | The notes Manage page at http://127.0.0.1:4017/admin/notes/manage, with the notes table and the Create, Edit, View, and Delete actions. |
| `orders-list-light.png` | `orders-list-dark.png` | The orders list at http://127.0.0.1:4017/admin/orders/list, with the orders table and a create action. |
| `orders-create-light.png` | `orders-create-dark.png` | The orders create page at http://127.0.0.1:4017/admin/orders/create, with the name field. |
| `orders-edit-light.png` | `orders-edit-dark.png` | The orders edit page at http://127.0.0.1:4017/admin/orders/edit, with the name field and the lines relation. |
| `orders-view-light.png` | `orders-view-dark.png` | The orders view page at http://127.0.0.1:4017/admin/orders/view, with the name infolist and the lines relation. |
| `desk-light.png` | `desk-dark.png` | The Desk page at http://127.0.0.1:4017/admin/desk, with the name field, the status combobox, the due picker, the infolist, the table, and the Edit header action. |
| `panel-navigation-light.png` | `panel-navigation-dark.png` | The hello-kotlin sidebar at http://127.0.0.1:4017/admin/desk, with the desk link and the Notes and Orders groups. |
| `panel-chrome-light.png` | `panel-chrome-dark.png` | The panel chrome at http://127.0.0.1:4017/admin/desk, showing the sidebar, top bar, page body, and footer. |
| `dialog-light.png` | `dialog-dark.png` | The Edit dialog on the Desk page at http://127.0.0.1:4017/admin/desk, opened from the Edit header action, with a note field inside. |
| `header-action-light.png` | `header-action-dark.png` | The Edit header action on the Desk page at http://127.0.0.1:4017/admin/desk. |
| `schema-section-light.png` | `schema-section-dark.png` | The Desk form at http://127.0.0.1:4017/admin/desk, a schema of the name, status, and due fields. |
| `schema-layout-light.png` | `schema-layout-dark.png` | The Desk page body at http://127.0.0.1:4017/admin/desk, with the form, the name infolist, and the notes table one after another. |
| `form-combobox-light.png` | `form-combobox-dark.png` | The status field on the Desk page at http://127.0.0.1:4017/admin/desk, opened as a combobox with Open and Done. |
| `form-native-select-light.png` | `form-native-select-dark.png` | The status field from the Desk form at http://127.0.0.1:4017/admin/desk, rendered with native() as a native select of Open and Done. |
| `form-date-picker-light.png` | `form-date-picker-dark.png` | The due field on the Desk page at http://127.0.0.1:4017/admin/desk, opened as the crafted month grid with hour and minute controls. |
| `form-native-date-light.png` | `form-native-date-dark.png` | The due field from the Desk form at http://127.0.0.1:4017/admin/desk, rendered with native() as a native datetime-local input. |
| `notes-infolist-light.png` | `notes-infolist-dark.png` | The name infolist on the Desk page at http://127.0.0.1:4017/admin/desk. |
| `notes-table-light.png` | `notes-table-dark.png` | The notes table at http://127.0.0.1:4017/admin/notes/manage, with the Ada row in the name column. |
| `saved-toast-light.png` | `saved-toast-dark.png` | A success toast titled Saved on a Firefly panel page, such as after a save on http://127.0.0.1:4017/admin/notes/manage. |
| `desk-widget-light.png` | `desk-widget-dark.png` | The Desk widget on the Desk page at http://127.0.0.1:4017/admin/desk, the paragraph that reads Open orders. |
| `tenant-menu-light.png` | `tenant-menu-dark.png` | A panel top bar with a tenant link labeled Acme that sets the tenant query parameter to acme. |
| `plugin-banner-light.png` | `plugin-banner-dark.png` | A panel body with the Banner text a plugin hook appends at the end of the body. |
| `export-dialog-light.png` | `export-dialog-dark.png` | The Export dialog opened from the export action, with the heading Export. |
| `themed-panel-light.png` | `themed-panel-dark.png` | The hello-kotlin panel at http://127.0.0.1:4017/admin/desk wearing the shipped theme on the sidebar and the page. |
