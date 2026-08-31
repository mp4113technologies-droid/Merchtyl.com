import { createTheme } from '@mui/material/styles';

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#14532d'
    },
    secondary: {
      main: '#b42318'
    },
    background: {
      default: '#f8fafc',
      paper: '#ffffff'
    }
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
    borderRadius: 8
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
          maxWidth: '100%',
          whiteSpace: 'normal'
        }
      }
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          maxWidth: 'calc(100% - 32px)',
          maxHeight: 'calc(100dvh - 32px)',
          margin: 16
        }
      }
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
          overflowWrap: 'anywhere'
        }
      }
    },
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
