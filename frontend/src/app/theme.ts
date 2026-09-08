import { createTheme } from '@mui/material/styles';

export const merchtylTokens = {
  colors: {
    navy: '#104477',
    navyDark: '#082F57',
    blue: '#1473E6',
    blueStrong: '#0B63CE',
    blueLight: '#EAF4FF',
    blueSoft: '#F5FAFF',
    page: '#F5F9FD',
    card: '#FFFFFF',
    subtle: '#F7FAFC',
    text: '#10233D',
    textSecondary: '#64748B',
    border: '#D9E6F2',
    borderStrong: '#B8D3EC',
    success: '#16A34A',
    warning: '#F59E0B',
    danger: '#DC2626'
  },
  radius: { small: 8, medium: 12, large: 16 },
  controlHeight: 44
} as const;

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: merchtylTokens.colors.blue,
      dark: merchtylTokens.colors.blueStrong,
      light: merchtylTokens.colors.blueLight,
      contrastText: '#FFFFFF'
    },
    secondary: {
      main: merchtylTokens.colors.navy
    },
    success: { main: merchtylTokens.colors.success },
    warning: { main: merchtylTokens.colors.warning },
    error: { main: merchtylTokens.colors.danger },
    info: { main: merchtylTokens.colors.blue },
    text: {
      primary: merchtylTokens.colors.text,
      secondary: merchtylTokens.colors.textSecondary
    },
    background: {
      default: merchtylTokens.colors.page,
      paper: merchtylTokens.colors.card
    },
    divider: merchtylTokens.colors.border
  },
  typography: {
    fontFamily: [
      'Inter',
      '-apple-system',
      'BlinkMacSystemFont',
      '"Segoe UI"',
      'sans-serif'
    ].join(','),
    h5: {
      fontWeight: 700
    },
    h4: {
      fontWeight: 700,
      fontSize: 'clamp(1.65rem, 3vw, 2.125rem)',
      overflowWrap: 'anywhere'
    },
    h6: {
      overflowWrap: 'anywhere'
    }
  },
  shape: {
    borderRadius: merchtylTokens.radius.small
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        '*, *::before, *::after': {
          boxSizing: 'border-box'
        },
        html: {
          minWidth: 0
        },
        body: {
          minWidth: 0,
          margin: 0
        },
        '#root': {
          minWidth: 0,
          minHeight: '100dvh'
        },
        'img, video, canvas, svg': {
          maxWidth: '100%'
        }
      }
    },
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          fontWeight: 700,
          borderRadius: merchtylTokens.radius.small,
          minHeight: 40,
          maxWidth: '100%',
          whiteSpace: 'normal'
        },
        containedPrimary: {
          backgroundColor: merchtylTokens.colors.blue,
          boxShadow: 'none',
          '&:hover': { backgroundColor: merchtylTokens.colors.blueStrong, boxShadow: 'none' }
        },
        outlinedPrimary: {
          borderColor: merchtylTokens.colors.borderStrong,
          color: merchtylTokens.colors.navy,
          '&:hover': { borderColor: merchtylTokens.colors.blue, backgroundColor: merchtylTokens.colors.blueLight }
        }
      }
    },
    MuiIconButton: {
      styleOverrides: { colorPrimary: { color: merchtylTokens.colors.blue } }
    },
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none' },
        outlined: { borderColor: merchtylTokens.colors.border, borderRadius: merchtylTokens.radius.medium }
      }
    },
    MuiCard: {
      styleOverrides: { root: { backgroundImage: 'none', borderColor: merchtylTokens.colors.border, borderRadius: merchtylTokens.radius.medium, boxShadow: 'none' } }
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          maxWidth: 'calc(100% - 32px)',
          maxHeight: 'calc(100dvh - 32px)',
          margin: 16,
          borderRadius: merchtylTokens.radius.medium,
          border: `1px solid ${merchtylTokens.colors.border}`,
          boxShadow: '0 18px 48px rgba(8, 47, 87, 0.14)'
        }
      }
    },
    MuiDialogTitle: {
      styleOverrides: { root: { color: merchtylTokens.colors.navy, fontWeight: 800 } }
    },
    MuiDialogActions: {
      styleOverrides: {
        root: {
          flexWrap: 'wrap',
          gap: 8
        }
      }
    },
    MuiDialogContent: {
      styleOverrides: {
        root: {
          overflowY: 'auto',
          minWidth: 0
        }
      }
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          maxWidth: 'calc(100vw - 24px)'
        }
      }
    },
    MuiMenu: {
      styleOverrides: {
        paper: {
          maxWidth: 'calc(100vw - 32px)',
          maxHeight: 'calc(100dvh - 96px)'
        }
      }
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          backgroundColor: merchtylTokens.colors.card,
          borderRadius: merchtylTokens.radius.small,
          '& .MuiOutlinedInput-notchedOutline': { borderColor: merchtylTokens.colors.borderStrong },
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: merchtylTokens.colors.blue },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: merchtylTokens.colors.blue }
        }
      }
    },
    MuiSelect: {
      styleOverrides: {
        select: {
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap'
        }
      }
    },
    MuiCardContent: {
      styleOverrides: {
        root: {
          '@media (max-width:1199.95px)': {
            padding: 16,
            '&:last-child': { paddingBottom: 16 }
          }
        }
      }
    },
    MuiToolbar: {
      styleOverrides: {
        root: {
          '@media (max-width:1199.95px)': { minHeight: 56 }
        }
      }
    },
    MuiGrid: {
      styleOverrides: {
        root: {
          minWidth: 0
        }
      }
    },
    MuiTableContainer: {
      styleOverrides: {
        root: {
          width: '100%',
          maxWidth: '100%',
          overflowX: 'auto'
        }
      }
    },
    MuiTableCell: {
      styleOverrides: {
        root: {
          overflowWrap: 'anywhere',
          borderBottomColor: merchtylTokens.colors.border
        },
        head: { backgroundColor: merchtylTokens.colors.blueSoft, color: merchtylTokens.colors.navy, fontWeight: 800 }
      }
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: merchtylTokens.radius.small,
          '&.Mui-selected': { backgroundColor: merchtylTokens.colors.blueLight, color: merchtylTokens.colors.blueStrong },
          '&.Mui-selected:hover': { backgroundColor: '#DCEEFF' }
        }
      }
    },
    MuiTabs: { styleOverrides: { indicator: { backgroundColor: merchtylTokens.colors.blue } } },
    MuiTab: { styleOverrides: { root: { textTransform: 'none', fontWeight: 700 } } },
    MuiChip: { styleOverrides: { root: { borderRadius: 7, fontWeight: 700 } } },
    MuiAlert: { styleOverrides: { root: { borderRadius: merchtylTokens.radius.small } } },
    MuiTextField: {
      styleOverrides: {
        root: {
          minWidth: 0,
          maxWidth: '100%'
        }
      }
    },
    MuiFormHelperText: {
      styleOverrides: {
        root: {
          overflowWrap: 'anywhere'
        }
      }
    }
  }
});

export const posTokens = {
  colors: { ...merchtylTokens.colors, muted: merchtylTokens.colors.subtle },
  radius: {
    control: 8,
    card: 12
  },
  controlHeight: 44
} as const;

export const posTheme = createTheme(theme, {
  palette: {
    primary: {
      main: posTokens.colors.blue,
      dark: posTokens.colors.navyDark,
      light: posTokens.colors.blueLight,
      contrastText: '#FFFFFF'
    },
    background: {
      default: posTokens.colors.page,
      paper: posTokens.colors.card
    },
    text: {
      primary: posTokens.colors.text,
      secondary: posTokens.colors.textSecondary
    },
    divider: posTokens.colors.border
  },
  shape: {
    borderRadius: posTokens.radius.control
  },
  components: {
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          '&.MuiPaper-outlined': {
            borderColor: posTokens.colors.border,
            borderRadius: posTokens.radius.card
          }
        }
      }
    },
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: posTokens.radius.control,
          minHeight: 40
        },
        containedPrimary: {
          backgroundColor: posTokens.colors.blue,
          '&:hover': { backgroundColor: posTokens.colors.blueStrong }
        },
        outlinedPrimary: {
          borderColor: posTokens.colors.borderStrong,
          color: posTokens.colors.navy,
          '&:hover': {
            borderColor: posTokens.colors.blue,
            backgroundColor: posTokens.colors.blueLight
          }
        }
      }
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          backgroundColor: posTokens.colors.card,
          '& .MuiOutlinedInput-notchedOutline': { borderColor: posTokens.colors.borderStrong },
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: posTokens.colors.blue },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: posTokens.colors.blue }
        }
      }
    },
    MuiTableCell: {
      styleOverrides: {
        head: {
          color: posTokens.colors.navy,
          backgroundColor: posTokens.colors.blueSoft,
          borderBottomColor: posTokens.colors.borderStrong,
          fontWeight: 700
        },
        root: { borderBottomColor: posTokens.colors.border }
      }
    },
    MuiDialogTitle: {
      styleOverrides: { root: { color: posTokens.colors.navy } }
    }
  }
});
