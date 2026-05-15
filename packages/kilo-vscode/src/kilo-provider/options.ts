export type KiloProviderOptions = {
  projectDirectory?: string | null
  slimEditMetadata?: boolean
  tabTitle?: (title: string) => void
  onSidebarVisibilityChange?: (visible: boolean) => void
  worktreeDirectories?: () => string[]
}
